# Nomi v2.10.1

### Counted foods and Gemini model suggestions

- Fix counted restaurant items such as “1 McDonald’s Cheeseburger” when the source reports nutrition per portion and the model omits the logged weight. Half portions and multiple items scale correctly.
- Use an explicitly printed portion weight to convert per-100 g nutrition for individual burgers, sandwiches, wraps and croissants.
- Resolve counted drinks such as “one Red Bull” from a printed can or bottle size, including German labels. Per-100 ml and per-container nutrition scale to the logged count without inventing a weight.
- Keep ambiguous container sizes and multi-item servings unresolved instead of treating them as one item.
- Suggest `gemini-3.8-flash` in the model buttons for Google Gemini and Exa + Gemini across all AI tasks. Existing saved model selections are preserved.

Install the attached APK over your existing Nomi installation to keep your data.
