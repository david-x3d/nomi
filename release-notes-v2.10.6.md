# Nomi v2.10.6

### More reliable food logging and corrections

- Rewriting a food now replaces its original entry and all grouped foods in one database transaction. If saving fails, the original meal remains intact.
- Text, photo, nutrition-label and barcode lookups share one request owner. Switching logging methods cancels the previous lookup, and its late results or citations cannot overwrite the new draft.
- A logging request keeps the day and entry it started with, even if you navigate to another day while it is running.
- Automatic and confirmed meals use the same save path, preserving food grouping, original wording and research references.
- The configured research fallback is tried before requesting a separate estimate. Estimates remain labeled, and Exa + OpenRouter keeps its price limits.
- Delete and Undo now share one controller. A failed delete restores the normal row, a failed Undo can be retried, and undo windows expire even after leaving Today.
- Internal cleanup separates menu scanning, capture, portion correction, evidence grounding and Today-screen interactions into focused components.

Install the attached APK over your existing Nomi installation to keep your data.
