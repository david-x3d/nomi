# Nomi v2.10.3

### Restaurant food without an amount

- Fix restaurant items logged without an amount, such as "Hans im Glück Classic Burger", failing with "the nutrition Nomi found is given for a serving it cannot convert to your amount" or a missing unit, even when Exa + Gemini found the right values on the restaurant's own page.
- A food named without an amount now logs one of it: a burger, sandwich, wrap or croissant counts as one piece, anything else as one serving. Nomi never guesses a weight for it.

### Read whole source pages

- New switch under Settings → AI provider → Food research, shown when research runs on Exa + Gemini. When it is on, Exa sends Gemini each page's full text, not only the excerpts it picks. Nomi then also finds headings and serving notes an excerpt leaves out, for example on restaurant nutrition pop-ups.
- It is off by default. Exa bills page text separately, and Gemini reads more text per lookup.

### AI debug log

- When every source is refused, the reported reason now comes from the page Gemini took the numbers from, not the last page tried. The reasons for the other pages follow in the log.
- A lookup that fails keeps the exact source text Gemini was given in the debug log, so a refused quote can be told apart from one the excerpt left out.

Install the attached APK over your existing Nomi installation to keep your data.
