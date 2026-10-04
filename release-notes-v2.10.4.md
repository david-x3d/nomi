# Nomi v2.10.4

### Exa + OpenRouter

- New food research provider under Settings → AI provider → Provider and model per task → Food research. Exa still finds the sources, and a cheap OpenRouter model reads them instead of Gemini. The default is z-ai/glm-5.3-flash; openai/gpt-6-luna and qwen/qwen3.8-flash are suggested too.
- It uses your OpenRouter key, so a key already saved for the fallback works without entering it again. The Exa key is shared with Exa + Gemini.
- Spending limits: Nomi only uses OpenRouter endpoints that cost at most $1 per million input and $5 per million output tokens. Each answer is capped at 8,000 tokens, and reasoning runs at low effort. If a model has no endpoint under that price, Nomi says so and sends nothing. For a hard spending limit, set a credit limit on the key at OpenRouter.

### Compare OpenRouter models

- New page under Settings → AI provider → Food research. Type a meal and pick up to four models, for example the three suggestions plus google/gemini-3.8-flash as a reference. Every model reads the same Exa search, so you pay for one search and all models see the same sources.
- Each model gets its own card with calories, protein, carbs and fat, whether the values are verified or estimated, the source, and the time it took. A model that fails shows its error without stopping the others. "Use for food research" makes that model the one food research runs on.

### Read whole source pages

- The switch now describes the AI in general instead of Gemini, since it works the same with Exa + OpenRouter.

Install the attached APK over your existing Nomi installation to keep your data.
