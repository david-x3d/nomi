# Nomi 2.13.0

- Add a **Reasoning** dropdown to every AI task in Settings (food research, food interpretation, portion changes, photo recognition and fallback): Default, Off, Low, Medium or High. Default sends nothing extra, so models that reject the setting keep working.
- Faster logging: an entry you have logged before is answered from the research cache without asking the interpretation model first.
- Faster logging: plain lists in which every food starts with its own amount, such as "2 Eier, 100 g Haferflocken und 1 Banane", are understood on the phone without an AI round trip.
- Faster research with Exa + Gemini: Gemini 2.5 Flash no longer spends time thinking before it reads the sources, unless you pick a reasoning level.
- Fix "The provider returned a response Nomi couldn't read" appearing for answers that were nearly right: a null where a list belongs, a quoted number, an answer split into text parts, or reasoning printed before the JSON are now read.
- An answer that still cannot be read is requested once more before an error is shown.
