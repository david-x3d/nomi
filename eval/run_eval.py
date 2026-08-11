#!/usr/bin/env python3
"""Validate, execute, and score Nomi's isolated Sonar vs Exa+Gemini benchmark."""

from __future__ import annotations

import argparse
import json
import math
import os
import pathlib
import re
import subprocess
import sys
import uuid
from datetime import datetime, timezone

ROOT = pathlib.Path(__file__).resolve().parents[1]
SUITE_PATH = ROOT / "eval" / "eval_cases.json"
RESULTS = ROOT / "eval" / "results"
MACROS = ("calories", "protein", "carbs", "fat")
OUTCOMES = {"success", "no_result", "needs_clarification", "reject_unverified"}
ENTITY_STOP = {
    "and", "the", "with", "from", "official", "nutrition", "original",
    "product", "produkt", "serving", "portion",
}
FAILURE_CATEGORIES = (
    "structured-output/schema failure",
    "JSON decoding failure",
    "quantity/parser validation failure",
    "source-grounding validation failure",
    "retrieval miss",
    "genuinely wrong nutrition extraction",
    "other",
)
SMOKE_CASE_IDS = (
    "real_nutella_de_100g",
    "real_nutella_de_15g_serving",
    "real_nutella_de_typo_20g",
    "real_coke_zero_pg_250ml",
    "real_pocky_matcha_one_box",
    "real_starbucks_uk_flat_white_short",
    "synthetic_protein_cereal_100g",
    "synthetic_combo_two_components",
    "noresult_fake_product_1",
    "fakecite_product_page",
)


def load_dotenv(path):
    if not path.exists():
        return
    for raw in path.read_text(encoding="utf-8-sig").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        name, value = line.split("=", 1)
        name, value = name.strip(), value.strip()
        if value[:1] == value[-1:] and value[:1] in {"'", '"'}:
            value = value[1:-1]
        if name and value:
            os.environ.setdefault(name, value)


def utc_now():
    return datetime.now(timezone.utc).isoformat()


def validate_suite(path=SUITE_PATH):
    data = json.loads(path.read_text(encoding="utf-8"))
    for key in ("schemaVersion", "suite", "semantics", "requiredCaseFields", "sources", "cases"):
        if key not in data:
            raise ValueError(f"Missing suite field: {key}")
    cases, meta = data["cases"], data["suite"]
    if meta["caseCount"] != len(cases):
        raise ValueError("suite.caseCount does not match cases length")
    ids = [case["id"] for case in cases]
    if len(ids) != len(set(ids)):
        raise ValueError("Eval case IDs are not unique")
    class_counts = {}
    outcome_counts = {}
    tag_counts = {}
    source_ids = {source["id"] for source in data["sources"]}
    required = data["requiredCaseFields"]
    for case in cases:
        for dotted in required:
            current = case
            for part in dotted.split("."):
                if not isinstance(current, dict) or part not in current:
                    raise ValueError(f"{case.get('id')} missing {dotted}")
                current = current[part]
        if case["expected"]["outcome"] not in OUTCOMES:
            raise ValueError(f"{case['id']} has invalid outcome")
        if not case["tags"] or any(not isinstance(tag, str) or not tag for tag in case["tags"]):
            raise ValueError(f"{case['id']} has invalid tags")
        if not set(MACROS).issubset(case["tolerances"]):
            raise ValueError(f"{case['id']} has incomplete tolerances")
        for metric in MACROS:
            expected = case["expected"][metric]
            tolerance = case["tolerances"][metric]
            values = (expected, tolerance["absolute"], tolerance["relativePercent"])
            if any(not isinstance(v, (int, float)) or not math.isfinite(v) or v < 0 for v in values):
                raise ValueError(f"{case['id']} has invalid numeric expectation/tolerance")
        amount = case["expected"]["amount"]
        if not isinstance(amount["value"], (int, float)) or not isinstance(amount["unit"], str):
            raise ValueError(f"{case['id']} has invalid expected amount")
        for metric, override in case.get("comparisonOverrides", {}).items():
            if metric not in MACROS or override.get("mode") != "range":
                raise ValueError(f"{case['id']} has invalid comparison override")
            if not override["min"] < override["maxExclusive"]:
                raise ValueError(f"{case['id']} has an empty comparison range")
        unknown_refs = set(case.get("sourceRefs", [])) - source_ids
        if unknown_refs:
            raise ValueError(f"{case['id']} references unknown sources: {sorted(unknown_refs)}")
        class_counts[case["dataClass"]] = class_counts.get(case["dataClass"], 0) + 1
        outcome = case["expected"]["outcome"]
        outcome_counts[outcome] = outcome_counts.get(outcome, 0) + 1
        for tag in case["tags"]:
            tag_counts[tag] = tag_counts.get(tag, 0) + 1
    if class_counts != meta["classCounts"]:
        raise ValueError(f"classCounts mismatch: {class_counts}")
    if outcome_counts != meta["outcomeCounts"]:
        raise ValueError(f"outcomeCounts mismatch: {outcome_counts}")
    if tag_counts != meta["tagCounts"]:
        raise ValueError("tagCounts mismatch")
    return data


def missing_keys(provider_mode="both"):
    missing = []
    if provider_mode in {"both", "sonar"} and not os.getenv("OPENROUTER_API_KEY"):
        missing.append("OPENROUTER_API_KEY")
    if provider_mode in {"both", "exa_gemini"}:
        if not os.getenv("GEMINI_API_KEY"):
            missing.append("GEMINI_API_KEY")
        if not os.getenv("EXA_API_KEY"):
            missing.append("EXA_API_KEY")
    return missing


def blocked_run(provider, suite, blocker):
    now = utc_now()
    return {
        "schemaVersion": "1.0.0",
        "provider": provider,
        "runStatus": "blocked",
        "suiteId": suite["suite"]["id"],
        "startedAt": now,
        "completedAt": now,
        "casesPlanned": len(suite["cases"]),
        "casesExecuted": 0,
        "cacheNamespace": None,
        "approximateApiCostUsd": None,
        "costNote": blocker,
        "summary": None,
        "cases": [],
    }


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def write_blocked(suite, missing):
    blocker = "Live benchmark not executed; missing environment variables: " + ", ".join(missing)
    sonar = blocked_run("sonar", suite, blocker)
    exa = blocked_run("exa_gemini", suite, blocker)
    comparison = {
        "schemaVersion": "1.0.0",
        "runStatus": "blocked",
        "casesCompared": 0,
        "winner": "not_demonstrated",
        "categoryDeltas": None,
        "biggestRegressions": [],
        "biggestImprovements": [],
        "blocker": blocker,
    }
    write_json(RESULTS / "sonar_latest.json", sonar)
    write_json(RESULTS / "exa_gemini_latest.json", exa)
    write_json(RESULTS / "comparison_latest.json", comparison)
    return blocker


def run_production_executor(overrides=None):
    env = os.environ.copy()
    env["NOMI_RUN_EVAL_LIVE"] = "1"
    env.update(overrides or {})
    sdk = ROOT.parent / "work" / "toolchains" / "android-sdk"
    if sdk.exists():
        env.setdefault("ANDROID_HOME", str(sdk))
        env.setdefault("ANDROID_SDK_ROOT", str(sdk))
    command = [
        str(ROOT / "gradlew.bat" if os.name == "nt" else ROOT / "gradlew"),
        ":app:testDebugUnitTest",
        "--tests", "com.nomi.app.data.remote.ai.NutritionBenchmarkEvalTest",
        "--console=plain", "--no-build-cache",
        "-Pksp.incremental=false", "-Pkotlin.incremental=false",
    ]
    subprocess.run(command, cwd=ROOT, env=env, check=True)


def canonical_url(value):
    return (value or "").strip().rstrip("/").lower()


def norm_unit(value):
    value = (value or "").strip().lower()
    replacements = {
        "grams": "g", "gram": "g", "millilitres": "ml", "milliliters": "ml",
        "servings": "serving", "items": "item",
    }
    return replacements.get(value, value)


def metric_ok(case, metric, actual):
    override = case.get("comparisonOverrides", {}).get(metric)
    if override:
        return override["min"] <= actual < override["maxExclusive"]
    expected = case["expected"][metric]
    tolerance = case["tolerances"][metric]
    allowed = max(tolerance["absolute"], abs(expected) * tolerance["relativePercent"] / 100)
    return abs(actual - expected) <= allowed


def actual_from_analysis(analysis):
    items = analysis.get("items", [])
    units = {norm_unit(item.get("unit")) for item in items}
    same_unit = len(units) == 1
    urls, names, bases = [], [], []
    for item in items:
        urls.extend([item.get("sourceUrl")] + item.get("supportingSourceUrls", []))
        names.append(item.get("sourceProductName") or item.get("name") or "")
        bases.append({
            "value": item.get("sourceServingQuantity"),
            "unit": item.get("sourceServingUnit"),
        })
    urls = list(dict.fromkeys(url for url in urls if url))
    return {
        "calories": sum(item.get("calories", 0) for item in items),
        "protein": sum(item.get("proteinGrams", 0) for item in items),
        "carbs": sum(item.get("carbohydrateGrams", 0) for item in items),
        "fat": sum(item.get("fatGrams", 0) for item in items),
        "amountValue": sum(item.get("quantity", 0) for item in items) if same_unit else None,
        "amountUnit": next(iter(units)) if same_unit and units else None,
        "productNames": names,
        "sourceUrls": urls,
        "sourceBases": bases,
        "isAllZero": bool(items) and all(
            item.get("calories", 0) == item.get("proteinGrams", 0) ==
            item.get("carbohydrateGrams", 0) == item.get("fatGrams", 0) == 0
            for item in items
        ),
        "fallbackUsed": any(item.get("isEstimate", False) for item in items),
    }


def amount_ok(actual, expected):
    if actual["amountValue"] is None:
        return False
    tolerance = max(0.01, abs(expected["value"]) * 0.01)
    return (
        norm_unit(actual["amountUnit"]) == norm_unit(expected["unit"])
        and abs(actual["amountValue"] - expected["value"]) <= tolerance
    )


def basis_ok(actual, basis):
    for source in actual["sourceBases"]:
        value, unit = source["value"], norm_unit(source["unit"])
        if value is None or value <= 0:
            return False
        if "per_100g" in basis and not (abs(value - 100) < .01 and unit == "g"):
            return False
        if "per_100ml" in basis and not (abs(value - 100) < .01 and unit == "ml"):
            return False
    return bool(actual["sourceBases"])


def entity_ok(case, actual, sources):
    corpus = (case["input"] + " " + " ".join(source["title"] for source in sources)).lower()
    for name in actual["productNames"]:
        tokens = [t for t in re.findall(r"[^\W_]{2,}", name.lower(), re.UNICODE)
                  if t not in ENTITY_STOP]
        if not tokens or sum(token in corpus for token in set(tokens)) < min(2, len(set(tokens))):
            return False
    return True


def citation_ok(actual, sources):
    allowed = {canonical_url(source["url"]) for source in sources}
    return bool(allowed) and any(canonical_url(url) in allowed for url in actual["sourceUrls"])


def percent(passed, count):
    return round(passed * 100 / count, 4) if count else None


def classify_failure(result, wrong_extraction=False):
    if wrong_extraction:
        return "genuinely wrong nutrition extraction"
    if result["status"] == "success":
        return None
    error_type = result.get("errorType") or ""
    message = (result.get("errorMessage") or "").lower()
    if error_type == "MissingFieldException" or "exactly one nutrition result" in message:
        return "structured-output/schema failure"
    if error_type == "JsonDecodingException":
        return "JSON decoding failure"
    if any(text in message for text in (
        "package percentage", "no food was recognized", "enter what you ate",
    )):
        return "quantity/parser validation failure"
    if any(text in message for text in (
        "does not support the claimed", "does not contain the claimed",
        "selected exa source", "unsupported citation", "unsupported source",
    )):
        return "source-grounding validation failure"
    if "could not verify nutrition data" in message or "no usable nutrition sources" in message:
        return "retrieval miss"
    return "other"


def score_run(raw, suite, require_full=True):
    all_cases = {case["id"]: case for case in suite["cases"]}
    sources = {source["id"]: source for source in suite["sources"]}
    raw_by_id = {case["id"]: case for case in raw["cases"]}
    if not set(raw_by_id).issubset(all_cases) or (require_full and set(raw_by_id) != set(all_cases)):
        raise ValueError(f"{raw['provider']} raw results do not match the full suite")
    case_by_id = {case["id"]: case for case in suite["cases"] if case["id"] in raw_by_id}
    scored = []
    for case_id in case_by_id:
        case, result = case_by_id[case_id], raw_by_id[case_id]
        expected, tags = case["expected"], case["tags"]
        success_expected = expected["outcome"] == "success"
        analysis = result.get("analysis")
        actual = actual_from_analysis(analysis) if analysis else None
        status = result["status"]
        outcome_ok = status == "success" if success_expected else status == "rejected"
        numeric = {metric: metric_ok(case, metric, actual[metric]) for metric in MACROS} if actual and success_expected else {m: None for m in MACROS}
        amount = amount_ok(actual, expected["amount"]) if actual and success_expected else None
        basis = basis_ok(actual, expected["nutritionBasis"]) if actual and success_expected else None
        allowed = [sources[ref] for ref in case.get("sourceRefs", [])]
        entity = entity_ok(case, actual, allowed) if actual and success_expected else None
        requires_citation = case.get("expectedBehavior", {}).get("requireVerifiedCitation", False)
        fake = "fake_citation" in tags
        citation = (
            citation_ok(actual, allowed) if requires_citation and actual
            else False if requires_citation
            else status == "rejected" if fake
            else True
        )
        zero_rule = (
            not success_expected if actual is None
            else "legitimate_all_zero" in tags if actual["isAllZero"] and success_expected
            else False if actual["isAllZero"]
            else True
        )
        retrieval = (
            case["dataClass"] == "verified_real" and success_expected and outcome_ok
            and all(numeric.values()) and amount and basis and entity
        )
        deterministic = (
            case["dataClass"] == "synthetic_deterministic" and success_expected
            and outcome_ok and all(numeric.values()) and amount and basis
        )
        reject = not success_expected and outcome_ok and zero_rule
        parts = [outcome_ok, zero_rule]
        parts += [value for value in numeric.values() if value is not None]
        parts += [value for value in (amount, basis, entity) if value is not None]
        if requires_citation or fake:
            parts.append(citation)
        wrong_extraction = status == "success" and (
            not success_expected or not all(numeric.values()) or amount is not True
            or basis is not True or entity is False
        )
        failure_category = (
            "source-grounding validation failure"
            if status == "success" and (requires_citation or fake) and not citation
            else classify_failure(result, wrong_extraction)
        )
        scored.append({
            "id": case_id,
            "dataClass": case["dataClass"],
            "tags": tags,
            "expectedOutcome": expected["outcome"],
            "actualOutcome": status,
            "latencyMillis": result["latencyMillis"],
            "actual": actual,
            "numeric": numeric,
            "amountAccuracy": amount,
            "nutritionBasisAccuracy": basis,
            "entityMatch": entity,
            "citationIntegrity": citation,
            "zeroResultRule": zero_rule,
            "retrievalAccuracy": bool(retrieval),
            "deterministicAccuracy": bool(deterministic),
            "rejectBehavior": bool(reject),
            "providerFailure": status == "provider_failure",
            "fallbackUsed": bool(actual and actual["fallbackUsed"]),
            "caseScorePercent": round(sum(parts) * 100 / len(parts), 4),
            "errorType": result.get("errorType"),
            "errorMessage": result.get("errorMessage"),
            "failureCategory": failure_category,
        })
    success = [c for c in scored if c["expectedOutcome"] == "success"]
    verified = [c for c in scored if c["dataClass"] == "verified_real" and c["expectedOutcome"] == "success"]
    synthetic = [c for c in scored if c["dataClass"] == "synthetic_deterministic" and c["expectedOutcome"] == "success"]
    citations = [c for c in scored if (c["dataClass"] == "verified_real" and c["expectedOutcome"] == "success") or "fake_citation" in c["tags"]]
    rejects = [c for c in scored if c["expectedOutcome"] != "success"]
    failure_counts = {
        category: sum(case["failureCategory"] == category for case in scored)
        for category in FAILURE_CATEGORIES
    }
    failure_categories = {
        category: {"count": count, "percent": percent(count, len(scored))}
        for category, count in failure_counts.items()
    }
    summary = {
        "verifiedRealAccuracyPercent": percent(sum(c["retrievalAccuracy"] for c in verified), len(verified)),
        "syntheticDeterministicPercent": percent(sum(c["deterministicAccuracy"] for c in synthetic), len(synthetic)),
        "citationIntegrityPercent": percent(sum(c["citationIntegrity"] for c in citations), len(citations)),
        "rejectBehaviorPercent": percent(sum(c["rejectBehavior"] for c in rejects), len(rejects)),
        "kcalAccuracyPercent": percent(sum(c["numeric"]["calories"] is True for c in success), len(success)),
        "proteinAccuracyPercent": percent(sum(c["numeric"]["protein"] is True for c in success), len(success)),
        "carbsAccuracyPercent": percent(sum(c["numeric"]["carbs"] is True for c in success), len(success)),
        "fatAccuracyPercent": percent(sum(c["numeric"]["fat"] is True for c in success), len(success)),
        "amountAccuracyPercent": percent(sum(c["amountAccuracy"] is True for c in success), len(success)),
        "nutritionBasisAccuracyPercent": percent(sum(c["nutritionBasisAccuracy"] is True for c in success), len(success)),
        "entityMatchPercent": percent(sum(c["entityMatch"] is True for c in success), len(success)),
        "averageLatencySeconds": round(sum(c["latencyMillis"] for c in scored) / len(scored) / 1000, 4),
        "latencyMeasurementValid": raw.get("latencyMeasurement") == "end_to_end_v1",
        "latencyNote": None if raw.get("latencyMeasurement") == "end_to_end_v1" else (
            "Historical raw run measured successful calls before completion; average latency is unreliable."
        ),
        "providerFailures": sum(c["providerFailure"] for c in scored),
        "fallbackUsage": sum(c["fallbackUsed"] for c in scored),
        "failureCategories": failure_categories,
    }
    return {
        **{key: raw.get(key) for key in ("schemaVersion", "provider", "cacheNamespace", "startedAt", "completedAt", "approximateApiCostUsd", "costNote", "latencyMeasurement")},
        "runStatus": "complete",
        "suiteId": suite["suite"]["id"],
        "casesPlanned": len(scored),
        "casesExecuted": len(scored),
        "summary": summary,
        "cases": scored,
    }


def comparison(sonar, exa):
    sonar_by_id = {case["id"]: case for case in sonar["cases"]}
    deltas = []
    for current in exa["cases"]:
        old = sonar_by_id[current["id"]]
        deltas.append({
            "id": current["id"],
            "sonarScorePercent": old["caseScorePercent"],
            "exaGeminiScorePercent": current["caseScorePercent"],
            "deltaPercent": round(current["caseScorePercent"] - old["caseScorePercent"], 4),
            "sonarOutcome": old["actualOutcome"],
            "exaGeminiOutcome": current["actualOutcome"],
        })
    s, e = sonar["summary"], exa["summary"]
    category = {
        "verifiedReal": e["verifiedRealAccuracyPercent"] - s["verifiedRealAccuracyPercent"],
        "syntheticDeterministic": e["syntheticDeterministicPercent"] - s["syntheticDeterministicPercent"],
        "citationIntegrity": e["citationIntegrityPercent"] - s["citationIntegrityPercent"],
        "rejectBehavior": e["rejectBehaviorPercent"] - s["rejectBehaviorPercent"],
        "averageLatencySeconds": (
            e["averageLatencySeconds"] - s["averageLatencySeconds"]
            if e["latencyMeasurementValid"] and s["latencyMeasurementValid"] else None
        ),
    }
    wins = category["verifiedReal"] > 0 and all(
        category[name] >= 0 for name in ("syntheticDeterministic", "citationIntegrity", "rejectBehavior")
    )
    return {
        "schemaVersion": "1.0.0",
        "runStatus": "complete",
        "casesCompared": len(deltas),
        "categoryDeltas": category,
        "winner": "exa_gemini" if wins else "not_demonstrated",
        "biggestRegressions": sorted(deltas, key=lambda d: d["deltaPercent"])[:15],
        "biggestImprovements": sorted(deltas, key=lambda d: d["deltaPercent"], reverse=True)[:15],
    }


def print_summary(sonar, exa, comp):
    for label, run in (("SONAR", sonar), ("EXA + GEMINI", exa)):
        s = run["summary"]
        print(label)
        print(f"verified_real accuracy: {s['verifiedRealAccuracyPercent']:.1f}%")
        print(f"synthetic deterministic: {s['syntheticDeterministicPercent']:.1f}%")
        print(f"citation integrity: {s['citationIntegrityPercent']:.1f}%")
        print(f"reject behavior: {s['rejectBehaviorPercent']:.1f}%")
        if s["latencyMeasurementValid"]:
            print(f"avg latency: {s['averageLatencySeconds']:.2f}s\n")
        else:
            print(f"avg latency: unreliable (historical raw recorded {s['averageLatencySeconds']:.2f}s)\n")
    print("DELTA")
    for key, value in comp["categoryDeltas"].items():
        if value is None:
            print(f"{key}: unavailable")
            continue
        suffix = "s" if key == "averageLatencySeconds" else "%"
        print(f"{key}: {value:+.2f}{suffix}")
    print("\n15 BIGGEST REGRESSIONS")
    for item in comp["biggestRegressions"]:
        print(f"{item['id']}: {item['deltaPercent']:+.1f}%")
    print("\n15 BIGGEST IMPROVEMENTS")
    for item in comp["biggestImprovements"]:
        print(f"{item['id']}: {item['deltaPercent']:+.1f}%")
    print(f"\nwinner: {comp['winner']}")


def print_failure_categories(run):
    print("EXA + GEMINI FAILURE CLASSIFICATION")
    for category in FAILURE_CATEGORIES:
        item = run["summary"]["failureCategories"][category]
        print(f"{category}: {item['count']} ({item['percent']:.2f}%)")
    print()


def score_existing_results(suite):
    sonar_raw = json.loads((RESULTS / "sonar_raw.json").read_text(encoding="utf-8"))
    exa_raw = json.loads((RESULTS / "exa_gemini_raw.json").read_text(encoding="utf-8"))
    sonar = score_run(sonar_raw, suite)
    exa = score_run(exa_raw, suite)
    comp = comparison(sonar, exa)
    write_json(RESULTS / "sonar_latest.json", sonar)
    write_json(RESULTS / "exa_gemini_latest.json", exa)
    write_json(RESULTS / "comparison_latest.json", comp)
    return sonar, exa, comp


def run_smoke(suite):
    known = {case["id"] for case in suite["cases"]}
    if not set(SMOKE_CASE_IDS).issubset(known):
        raise ValueError("The representative smoke set references missing eval cases")
    run_production_executor({
        "NOMI_EVAL_PROVIDER": "exa_gemini",
        "NOMI_EVAL_CASE_IDS": ",".join(SMOKE_CASE_IDS),
        "NOMI_EVAL_OUTPUT_SUFFIX": "_smoke",
    })
    raw = json.loads((RESULTS / "exa_gemini_smoke_raw.json").read_text(encoding="utf-8"))
    scored = score_run(raw, suite, require_full=False)
    contract_valid = sum(
        case["failureCategory"] not in {
            "structured-output/schema failure", "JSON decoding failure", "other",
        }
        and not case["providerFailure"]
        for case in scored["cases"]
    )
    expected_passes = sum(
        (case["expectedOutcome"] == "success" and case["actualOutcome"] == "success")
        or (case["expectedOutcome"] != "success" and case["actualOutcome"] == "rejected")
        for case in scored["cases"]
    )
    gate_passed = contract_valid == len(SMOKE_CASE_IDS) and expected_passes == len(SMOKE_CASE_IDS)
    scored["smokeGate"] = {
        "selectedCaseIds": list(SMOKE_CASE_IDS),
        "structuredContractValid": contract_valid,
        "structuredContractValidPercent": percent(contract_valid, len(SMOKE_CASE_IDS)),
        "expectedOutcomePasses": expected_passes,
        "expectedOutcomePassPercent": percent(expected_passes, len(SMOKE_CASE_IDS)),
        "gatePassed": gate_passed,
        "fullRerunAllowed": gate_passed,
    }
    write_json(RESULTS / "exa_gemini_smoke_latest.json", scored)
    print(f"SMOKE structured contract: {contract_valid}/{len(SMOKE_CASE_IDS)}")
    print(f"SMOKE expected outcomes: {expected_passes}/{len(SMOKE_CASE_IDS)}")
    print(f"SMOKE full rerun allowed: {gate_passed}")
    for case in scored["cases"]:
        print(f"{case['id']}: {case['actualOutcome']} | {case['failureCategory'] or 'valid result'}")
    return gate_passed


def main():
    parser = argparse.ArgumentParser()
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument("--live", action="store_true", help="Execute all providers after a passing smoke gate")
    modes.add_argument("--smoke", action="store_true", help="Execute the representative 10-case Exa-only smoke")
    modes.add_argument("--score-existing", action="store_true", help="Rescore existing raw files without API calls")
    args = parser.parse_args()
    suite = validate_suite()
    print(f"Validated {len(suite['cases'])} cases from {SUITE_PATH}")
    load_dotenv(ROOT / "eval" / ".env")
    requested_provider_mode = "exa_gemini" if args.smoke else "both"
    missing = missing_keys(requested_provider_mode)
    if args.score_existing:
        sonar, exa, comp = score_existing_results(suite)
        print_failure_categories(exa)
        print_summary(sonar, exa, comp)
        return 0
    if not (args.live or args.smoke):
        blocker = write_blocked(suite, missing or ["no execution mode was supplied"])
        print(f"Preflight complete; 0 live cases executed. {blocker}")
        return 0
    if missing:
        blocker = write_blocked(suite, missing)
        print(blocker, file=sys.stderr)
        return 2
    if args.smoke:
        run_smoke(suite)
        return 0
    smoke_path = RESULTS / "exa_gemini_smoke_latest.json"
    if not smoke_path.exists():
        print("Full live rerun refused: run --smoke first.", file=sys.stderr)
        return 3
    smoke = json.loads(smoke_path.read_text(encoding="utf-8"))
    if not smoke.get("smokeGate", {}).get("gatePassed", False):
        print("Full live rerun refused: the latest 10-case smoke gate did not pass.", file=sys.stderr)
        return 3
    run_production_executor()
    sonar, exa, comp = score_existing_results(suite)
    print_failure_categories(exa)
    print_summary(sonar, exa, comp)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
