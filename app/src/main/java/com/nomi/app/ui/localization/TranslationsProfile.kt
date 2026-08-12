package com.nomi.app.ui.localization

/** Profile and goals, the nutrition plan editor, micronutrients and the Progress page. */
internal val profileTranslations: Map<String, NomiTranslation> = mapOf(
    // Profile and goals
    "Profile & goals" to NomiTranslation(
        de = "Profil & Ziele", es = "Perfil y objetivos", fr = "Profil et objectifs",
        it = "Profilo e obiettivi", nl = "Profiel en doelen", pt = "Perfil e objetivos",
        sq = "Profili dhe objektivat", sv = "Profil och mål", tr = "Profil ve hedefler",
    ),
    "Your calculation inputs" to NomiTranslation(
        de = "Deine Berechnungsdaten", es = "Tus datos de cálculo",
        fr = "Tes données de calcul", it = "I tuoi dati di calcolo",
        nl = "Je berekeningsgegevens", pt = "Os teus dados de cálculo",
        sq = "Të dhënat e tua të llogaritjes", sv = "Dina beräkningsuppgifter",
        tr = "Hesaplama verilerin",
    ),
    "Saving creates a new plan" to NomiTranslation(
        de = "Beim Speichern wird ein neuer Plan erstellt", es = "Al guardar se crea un plan nuevo",
        fr = "L’enregistrement crée un nouveau plan", it = "Il salvataggio crea un nuovo piano",
        nl = "Opslaan maakt een nieuw plan aan", pt = "Guardar cria um novo plano",
        sq = "Ruajtja krijon një plan të ri", sv = "Att spara skapar en ny plan",
        tr = "Kaydetmek yeni bir plan oluşturur",
    ),
    "Nomi recalculates from the updated profile and starts a new plan version. Previous days " +
        "keep the targets they originally used." to NomiTranslation(
        de = "Nomi berechnet anhand des aktualisierten Profils neu und startet eine neue " +
            "Planversion. Frühere Tage behalten ihre ursprünglichen Ziele.",
        es = "Nomi recalcula a partir del perfil actualizado e inicia una nueva versión del " +
            "plan. Los días anteriores conservan los objetivos que usaron originalmente.",
        fr = "Nomi recalcule à partir du profil mis à jour et démarre une nouvelle version du " +
            "plan. Les journées précédentes conservent leurs objectifs d’origine.",
        it = "Nomi ricalcola dal profilo aggiornato e avvia una nuova versione del piano. I " +
            "giorni precedenti mantengono gli obiettivi originali.",
        nl = "Nomi rekent opnieuw met het bijgewerkte profiel en start een nieuwe planversie. " +
            "Eerdere dagen houden hun oorspronkelijke doelen.",
        pt = "A Nomi recalcula a partir do perfil atualizado e inicia uma nova versão do plano. " +
            "Os dias anteriores mantêm os objetivos originais.",
        sq = "Nomi rillogarit nga profili i përditësuar dhe nis një version të ri të planit. " +
            "Ditët e mëparshme ruajnë objektivat origjinale.",
        sv = "Nomi räknar om utifrån den uppdaterade profilen och startar en ny planversion. " +
            "Tidigare dagar behåller sina ursprungliga mål.",
        tr = "Nomi güncellenen profilden yeniden hesaplar ve yeni bir plan sürümü başlatır. " +
            "Önceki günler özgün hedeflerini korur.",
    ),
    "Date of birth" to NomiTranslation(
        de = "Geburtsdatum", es = "Fecha de nacimiento", fr = "Date de naissance",
        it = "Data di nascita", nl = "Geboortedatum", pt = "Data de nascimento",
        sq = "Data e lindjes", sv = "Födelsedatum", tr = "Doğum tarihi",
    ),
    "YYYY-MM-DD" to NomiTranslation(
        de = "JJJJ-MM-TT", es = "AAAA-MM-DD", fr = "AAAA-MM-JJ", it = "AAAA-MM-GG",
        nl = "JJJJ-MM-DD", pt = "AAAA-MM-DD", sq = "VVVV-MM-DD", sv = "ÅÅÅÅ-MM-DD",
        tr = "YYYY-AA-GG",
    ),
    "Stored as a date so your age updates automatically." to NomiTranslation(
        de = "Als Datum gespeichert, damit dein Alter automatisch aktualisiert wird.",
        es = "Se guarda como fecha para que tu edad se actualice sola.",
        fr = "Enregistrée comme date pour que ton âge se mette à jour automatiquement.",
        it = "Salvata come data, così la tua età si aggiorna da sola.",
        nl = "Opgeslagen als datum, zodat je leeftijd automatisch wordt bijgewerkt.",
        pt = "Guardada como data para a tua idade se atualizar automaticamente.",
        sq = "Ruhet si datë, që mosha jote të përditësohet automatikisht.",
        sv = "Sparas som datum så att din ålder uppdateras automatiskt.",
        tr = "Yaşın otomatik güncellensin diye tarih olarak saklanır.",
    ),
    "Energy calculation" to NomiTranslation(
        de = "Energieberechnung", es = "Cálculo de energía", fr = "Calcul énergétique",
        it = "Calcolo energetico", nl = "Energieberekening", pt = "Cálculo de energia",
        sq = "Llogaritja e energjisë", sv = "Energiberäkning", tr = "Enerji hesabı",
    ),
    "Female equation" to NomiTranslation(
        de = "Formel für Frauen", es = "Ecuación femenina", fr = "Équation féminine",
        it = "Equazione femminile", nl = "Vrouwelijke formule", pt = "Equação feminina",
        sq = "Formula për femra", sv = "Kvinnlig formel", tr = "Kadın denklemi",
    ),
    "Male equation" to NomiTranslation(
        de = "Formel für Männer", es = "Ecuación masculina", fr = "Équation masculine",
        it = "Equazione maschile", nl = "Mannelijke formule", pt = "Equação masculina",
        sq = "Formula për meshkuj", sv = "Manlig formel", tr = "Erkek denklemi",
    ),
    "Manual energy target" to NomiTranslation(
        de = "Manuelles Energieziel", es = "Objetivo de energía manual",
        fr = "Objectif énergétique manuel", it = "Obiettivo energetico manuale",
        nl = "Handmatig energiedoel", pt = "Objetivo de energia manual",
        sq = "Objektiv manual energjie", sv = "Manuellt energimål", tr = "Elle enerji hedefi",
    ),
    "Height" to NomiTranslation(
        de = "Körpergröße", es = "Altura", fr = "Taille", it = "Altezza", nl = "Lengte",
        pt = "Altura", sq = "Gjatësia", sv = "Längd", tr = "Boy",
    ),
    "Goal" to NomiTranslation(
        de = "Ziel", es = "Objetivo", fr = "Objectif", it = "Obiettivo", nl = "Doel",
        pt = "Objetivo", sq = "Objektivi", sv = "Mål", tr = "Hedef",
    ),
    "Target weight" to NomiTranslation(
        de = "Zielgewicht", es = "Peso objetivo", fr = "Poids cible", it = "Peso obiettivo",
        nl = "Streefgewicht", pt = "Peso alvo", sq = "Pesha e synuar", sv = "Målvikt",
        tr = "Hedef kilo",
    ),
    "Activity level" to NomiTranslation(
        de = "Aktivitätsniveau", es = "Nivel de actividad", fr = "Niveau d’activité",
        it = "Livello di attività", nl = "Activiteitsniveau", pt = "Nível de atividade",
        sq = "Niveli i aktivitetit", sv = "Aktivitetsnivå", tr = "Aktivite düzeyi",
    ),
    "Mostly seated" to NomiTranslation(
        de = "Überwiegend sitzend", es = "Mayormente sentado", fr = "Principalement assis",
        it = "Prevalentemente seduto", nl = "Vooral zittend", pt = "Sobretudo sentado",
        sq = "Kryesisht ulur", sv = "Mest stillasittande", tr = "Çoğunlukla oturarak",
    ),
    "Lightly active" to NomiTranslation(
        de = "Leicht aktiv", es = "Poco activo", fr = "Peu actif", it = "Poco attivo",
        nl = "Licht actief", pt = "Pouco ativo", sq = "Pak aktiv", sv = "Lätt aktiv",
        tr = "Hafif aktif",
    ),
    "Active" to NomiTranslation(
        de = "Aktiv", es = "Activo", fr = "Actif", it = "Attivo", nl = "Actief",
        pt = "Ativo", sq = "Aktiv", sv = "Aktiv", tr = "Aktif",
    ),
    "Very active" to NomiTranslation(
        de = "Sehr aktiv", es = "Muy activo", fr = "Très actif", it = "Molto attivo",
        nl = "Zeer actief", pt = "Muito ativo", sq = "Shumë aktiv", sv = "Mycket aktiv",
        tr = "Çok aktif",
    ),
    "Lose" to NomiTranslation(
        de = "Abnehmen", es = "Perder", fr = "Perdre", it = "Dimagrire", nl = "Afvallen",
        pt = "Perder", sq = "Humb peshë", sv = "Gå ner", tr = "Vermek",
    ),
    "Maintain" to NomiTranslation(
        de = "Halten", es = "Mantener", fr = "Maintenir", it = "Mantenere", nl = "Behouden",
        pt = "Manter", sq = "Ruaj peshën", sv = "Behålla", tr = "Korumak",
    ),
    "Gain" to NomiTranslation(
        de = "Zunehmen", es = "Ganar", fr = "Prendre", it = "Aumentare", nl = "Aankomen",
        pt = "Ganhar", sq = "Shto peshë", sv = "Gå upp", tr = "Almak",
    ),
    "Gentle gain" to NomiTranslation(
        de = "Langsame Zunahme", es = "Aumento suave", fr = "Prise progressive",
        it = "Aumento graduale", nl = "Rustig aankomen", pt = "Ganho suave",
        sq = "Shtim i butë", sv = "Långsam ökning", tr = "Yavaş alım",
    ),
    "Moderate gain" to NomiTranslation(
        de = "Moderate Zunahme", es = "Aumento moderado", fr = "Prise modérée",
        it = "Aumento moderato", nl = "Gematigd aankomen", pt = "Ganho moderado",
        sq = "Shtim i moderuar", sv = "Måttlig ökning", tr = "Ilımlı alım",
    ),
    "Gentle" to NomiTranslation(
        de = "Langsam", es = "Suave", fr = "Progressif", it = "Graduale", nl = "Rustig",
        pt = "Suave", sq = "I butë", sv = "Långsam", tr = "Yavaş",
    ),
    "Moderate" to NomiTranslation(
        de = "Moderat", es = "Moderado", fr = "Modéré", it = "Moderato", nl = "Gematigd",
        pt = "Moderado", sq = "I moderuar", sv = "Måttlig", tr = "Ilımlı",
    ),
    "Faster" to NomiTranslation(
        de = "Schneller", es = "Más rápido", fr = "Plus rapide", it = "Più rapido",
        nl = "Sneller", pt = "Mais rápido", sq = "Më i shpejtë", sv = "Snabbare",
        tr = "Daha hızlı",
    ),
    "Progression rate" to NomiTranslation(
        de = "Tempo", es = "Ritmo de progresión", fr = "Rythme de progression",
        it = "Ritmo di progressione", nl = "Tempo", pt = "Ritmo de progressão",
        sq = "Ritmi i përparimit", sv = "Takt", tr = "İlerleme hızı",
    ),
    "Keep custom targets, if set" to NomiTranslation(
        de = "Benutzerdefinierte Ziele beibehalten",
        es = "Conservar los objetivos personalizados, si los hay",
        fr = "Conserver les objectifs personnalisés, le cas échéant",
        it = "Mantieni gli obiettivi personalizzati, se impostati",
        nl = "Aangepaste doelen behouden, indien ingesteld",
        pt = "Manter os objetivos personalizados, se definidos",
        sq = "Ruaj objektivat e personalizuara, nëse janë vendosur",
        sv = "Behåll egna mål, om de finns",
        tr = "Varsa özel hedefleri koru",
    ),
    "Carry your custom calorie and macro targets into the new plan instead of replacing them " +
        "with calculated targets." to NomiTranslation(
        de = "Übernimm deine benutzerdefinierten Kalorien- und Makroziele in den neuen Plan, " +
            "statt sie durch berechnete Ziele zu ersetzen.",
        es = "Lleva tus objetivos personalizados de calorías y macros al nuevo plan en lugar de " +
            "sustituirlos por los calculados.",
        fr = "Reporte tes objectifs personnalisés de calories et de macros dans le nouveau plan " +
            "au lieu de les remplacer par des objectifs calculés.",
        it = "Porta i tuoi obiettivi personalizzati di calorie e macro nel nuovo piano invece di " +
            "sostituirli con quelli calcolati.",
        nl = "Neem je aangepaste calorie- en macrodoelen mee naar het nieuwe plan in plaats van " +
            "ze te vervangen door berekende doelen.",
        pt = "Leva os teus objetivos personalizados de calorias e macros para o novo plano em vez " +
            "de os substituir pelos calculados.",
        sq = "Bart objektivat e tua të personalizuara të kalorive dhe makrove në planin e ri, në " +
            "vend që t’i zëvendësosh me objektiva të llogaritura.",
        sv = "Ta med dina egna kalori- och makromål till den nya planen i stället för att ersätta " +
            "dem med beräknade mål.",
        tr = "Özel kalori ve makro hedeflerini hesaplananlarla değiştirmek yerine yeni plana taşı.",
    ),
    "Check the highlighted fields." to NomiTranslation(
        de = "Prüfe die markierten Felder.", es = "Revisa los campos marcados.",
        fr = "Vérifie les champs signalés.", it = "Controlla i campi evidenziati.",
        nl = "Controleer de gemarkeerde velden.", pt = "Verifica os campos assinalados.",
        sq = "Kontrollo fushat e theksuara.", sv = "Kontrollera de markerade fälten.",
        tr = "İşaretli alanları kontrol et.",
    ),
    "Save and create new plan" to NomiTranslation(
        de = "Speichern und neuen Plan erstellen", es = "Guardar y crear un plan nuevo",
        fr = "Enregistrer et créer un nouveau plan", it = "Salva e crea un nuovo piano",
        nl = "Opslaan en nieuw plan maken", pt = "Guardar e criar novo plano",
        sq = "Ruaj dhe krijo plan të ri", sv = "Spara och skapa ny plan",
        tr = "Kaydet ve yeni plan oluştur",
    ),

    // Profile validation messages, keyed by the English text the view model produces.
    "Use a valid date in YYYY-MM-DD format." to NomiTranslation(
        de = "Gib ein gültiges Datum im Format JJJJ-MM-TT ein.",
        es = "Usa una fecha válida con el formato AAAA-MM-DD.",
        fr = "Utilise une date valide au format AAAA-MM-JJ.",
        it = "Usa una data valida nel formato AAAA-MM-GG.",
        nl = "Gebruik een geldige datum in de notatie JJJJ-MM-DD.",
        pt = "Usa uma data válida no formato AAAA-MM-DD.",
        sq = "Përdor një datë të vlefshme në formatin VVVV-MM-DD.",
        sv = "Ange ett giltigt datum i formatet ÅÅÅÅ-MM-DD.",
        tr = "YYYY-AA-GG biçiminde geçerli bir tarih gir.",
    ),
    "Date of birth can't be in the future." to NomiTranslation(
        de = "Das Geburtsdatum darf nicht in der Zukunft liegen.",
        es = "La fecha de nacimiento no puede estar en el futuro.",
        fr = "La date de naissance ne peut pas être dans le futur.",
        it = "La data di nascita non può essere nel futuro.",
        nl = "De geboortedatum kan niet in de toekomst liggen.",
        pt = "A data de nascimento não pode estar no futuro.",
        sq = "Data e lindjes nuk mund të jetë në të ardhmen.",
        sv = "Födelsedatumet kan inte ligga i framtiden.",
        tr = "Doğum tarihi gelecekte olamaz.",
    ),
    "Nomi supports ages 13 to 120." to NomiTranslation(
        de = "Nomi unterstützt ein Alter von 13 bis 120 Jahren.",
        es = "Nomi admite edades de 13 a 120 años.",
        fr = "Nomi accepte les âges de 13 à 120 ans.",
        it = "Nomi supporta età da 13 a 120 anni.",
        nl = "Nomi ondersteunt leeftijden van 13 tot 120 jaar.",
        pt = "A Nomi suporta idades dos 13 aos 120 anos.",
        sq = "Nomi mbështet mosha nga 13 deri në 120 vjeç.",
        sv = "Nomi stöder åldrar från 13 till 120 år.",
        tr = "Nomi 13 ile 120 yaş arasını destekler.",
    ),
    "Choose an energy calculation option." to NomiTranslation(
        de = "Wähle eine Option für die Energieberechnung.",
        es = "Elige una opción de cálculo de energía.",
        fr = "Choisis une option de calcul énergétique.",
        it = "Scegli un’opzione di calcolo energetico.",
        nl = "Kies een optie voor de energieberekening.",
        pt = "Escolhe uma opção de cálculo de energia.",
        sq = "Zgjidh një opsion për llogaritjen e energjisë.",
        sv = "Välj ett alternativ för energiberäkning.",
        tr = "Bir enerji hesaplama seçeneği seç.",
    ),
    "Keep custom targets or choose an equation so Nomi can calculate the new plan." to
        NomiTranslation(
            de = "Behalte benutzerdefinierte Ziele bei oder wähle eine Formel, damit Nomi den " +
                "neuen Plan berechnen kann.",
            es = "Conserva los objetivos personalizados o elige una ecuación para que Nomi pueda " +
                "calcular el nuevo plan.",
            fr = "Conserve les objectifs personnalisés ou choisis une équation pour que Nomi " +
                "puisse calculer le nouveau plan.",
            it = "Mantieni gli obiettivi personalizzati oppure scegli un’equazione così Nomi può " +
                "calcolare il nuovo piano.",
            nl = "Behoud aangepaste doelen of kies een formule zodat Nomi het nieuwe plan kan " +
                "berekenen.",
            pt = "Mantém os objetivos personalizados ou escolhe uma equação para a Nomi poder " +
                "calcular o novo plano.",
            sq = "Ruaj objektivat e personalizuara ose zgjidh një formulë që Nomi të llogarisë " +
                "planin e ri.",
            sv = "Behåll egna mål eller välj en formel så att Nomi kan beräkna den nya planen.",
            tr = "Nomi yeni planı hesaplayabilsin diye özel hedefleri koru ya da bir denklem seç.",
        ),
    "Enter your height." to NomiTranslation(
        de = "Gib deine Körpergröße ein.", es = "Introduce tu altura.", fr = "Saisis ta taille.",
        it = "Inserisci la tua altezza.", nl = "Voer je lengte in.", pt = "Introduz a tua altura.",
        sq = "Fut gjatësinë tënde.", sv = "Ange din längd.", tr = "Boyunu gir.",
    ),
    "Height must be between 100 and 250 cm." to NomiTranslation(
        de = "Die Körpergröße muss zwischen 100 und 250 cm liegen.",
        es = "La altura debe estar entre 100 y 250 cm.",
        fr = "La taille doit être comprise entre 100 et 250 cm.",
        it = "L’altezza deve essere compresa tra 100 e 250 cm.",
        nl = "De lengte moet tussen 100 en 250 cm liggen.",
        pt = "A altura tem de estar entre 100 e 250 cm.",
        sq = "Gjatësia duhet të jetë midis 100 dhe 250 cm.",
        sv = "Längden måste vara mellan 100 och 250 cm.",
        tr = "Boy 100 ile 250 cm arasında olmalı.",
    ),
    "Enter a target weight." to NomiTranslation(
        de = "Gib ein Zielgewicht ein.", es = "Introduce un peso objetivo.",
        fr = "Saisis un poids cible.", it = "Inserisci un peso obiettivo.",
        nl = "Voer een streefgewicht in.", pt = "Introduz um peso alvo.",
        sq = "Fut një peshë të synuar.", sv = "Ange en målvikt.", tr = "Bir hedef kilo gir.",
    ),
    "Target weight must be between 30 and 400 kg." to NomiTranslation(
        de = "Das Zielgewicht muss zwischen 30 und 400 kg liegen.",
        es = "El peso objetivo debe estar entre 30 y 400 kg.",
        fr = "Le poids cible doit être compris entre 30 et 400 kg.",
        it = "Il peso obiettivo deve essere compreso tra 30 e 400 kg.",
        nl = "Het streefgewicht moet tussen 30 en 400 kg liggen.",
        pt = "O peso alvo tem de estar entre 30 e 400 kg.",
        sq = "Pesha e synuar duhet të jetë midis 30 dhe 400 kg.",
        sv = "Målvikten måste vara mellan 30 och 400 kg.",
        tr = "Hedef kilo 30 ile 400 kg arasında olmalı.",
    ),
    "For weight loss, the target must be below your current weight." to NomiTranslation(
        de = "Beim Abnehmen muss das Ziel unter deinem aktuellen Gewicht liegen.",
        es = "Para perder peso, el objetivo debe ser inferior a tu peso actual.",
        fr = "Pour perdre du poids, l’objectif doit être inférieur à ton poids actuel.",
        it = "Per dimagrire, l’obiettivo deve essere inferiore al tuo peso attuale.",
        nl = "Om af te vallen moet het doel onder je huidige gewicht liggen.",
        pt = "Para perder peso, o objetivo tem de ser inferior ao teu peso atual.",
        sq = "Për humbje peshe, objektivi duhet të jetë nën peshën tënde aktuale.",
        sv = "För viktnedgång måste målet ligga under din nuvarande vikt.",
        tr = "Kilo vermek için hedef, mevcut kilonun altında olmalı.",
    ),
    "For weight gain, the target must be above your current weight." to NomiTranslation(
        de = "Beim Zunehmen muss das Ziel über deinem aktuellen Gewicht liegen.",
        es = "Para ganar peso, el objetivo debe ser superior a tu peso actual.",
        fr = "Pour prendre du poids, l’objectif doit être supérieur à ton poids actuel.",
        it = "Per aumentare di peso, l’obiettivo deve essere superiore al tuo peso attuale.",
        nl = "Om aan te komen moet het doel boven je huidige gewicht liggen.",
        pt = "Para ganhar peso, o objetivo tem de ser superior ao teu peso atual.",
        sq = "Për shtim peshe, objektivi duhet të jetë mbi peshën tënde aktuale.",
        sv = "För viktuppgång måste målet ligga över din nuvarande vikt.",
        tr = "Kilo almak için hedef, mevcut kilonun üzerinde olmalı.",
    ),
    "Choose a progression rate." to NomiTranslation(
        de = "Wähle ein Tempo.", es = "Elige un ritmo de progresión.",
        fr = "Choisis un rythme de progression.", it = "Scegli un ritmo di progressione.",
        nl = "Kies een tempo.", pt = "Escolhe um ritmo de progressão.",
        sq = "Zgjidh një ritëm përparimi.", sv = "Välj en takt.", tr = "Bir ilerleme hızı seç.",
    ),
    "Choose a preset rate when recalculating from profile settings." to NomiTranslation(
        de = "Wähle bei der Neuberechnung in den Profileinstellungen ein vorgegebenes Tempo.",
        es = "Elige un ritmo predefinido al recalcular desde los ajustes del perfil.",
        fr = "Choisis un rythme prédéfini lors d’un recalcul depuis les réglages du profil.",
        it = "Scegli un ritmo predefinito quando ricalcoli dalle impostazioni del profilo.",
        nl = "Kies een vooraf ingesteld tempo bij het herberekenen vanuit de profielinstellingen.",
        pt = "Escolhe um ritmo predefinido ao recalcular a partir das definições do perfil.",
        sq = "Zgjidh një ritëm të paracaktuar kur rillogarit nga cilësimet e profilit.",
        sv = "Välj en förinställd takt när du räknar om från profilinställningarna.",
        tr = "Profil ayarlarından yeniden hesaplarken hazır bir hız seç.",
    ),
    "Faster is available for loss plans only." to NomiTranslation(
        de = "„Schneller“ ist nur für Abnehmpläne verfügbar.",
        es = "«Más rápido» solo está disponible en los planes de pérdida de peso.",
        fr = "« Plus rapide » n’est disponible que pour les plans de perte de poids.",
        it = "«Più rapido» è disponibile solo per i piani di dimagrimento.",
        nl = "“Sneller” is alleen beschikbaar voor afvalplannen.",
        pt = "«Mais rápido» só está disponível em planos de perda de peso.",
        sq = "“Më i shpejtë” ofrohet vetëm për planet e humbjes së peshës.",
        sv = "”Snabbare” finns bara för viktnedgångsplaner.",
        tr = "“Daha hızlı” yalnızca kilo verme planlarında kullanılabilir.",
    ),
    "Choose a goal." to NomiTranslation(
        de = "Wähle ein Ziel.", es = "Elige un objetivo.", fr = "Choisis un objectif.",
        it = "Scegli un obiettivo.", nl = "Kies een doel.", pt = "Escolhe um objetivo.",
        sq = "Zgjidh një objektiv.", sv = "Välj ett mål.", tr = "Bir hedef seç.",
    ),
    "Choose an activity level." to NomiTranslation(
        de = "Wähle ein Aktivitätsniveau.", es = "Elige un nivel de actividad.",
        fr = "Choisis un niveau d’activité.", it = "Scegli un livello di attività.",
        nl = "Kies een activiteitsniveau.", pt = "Escolhe um nível de atividade.",
        sq = "Zgjidh një nivel aktiviteti.", sv = "Välj en aktivitetsnivå.",
        tr = "Bir aktivite düzeyi seç.",
    ),
    "Current weight stays at {0} kg. Record a new weight from Progress instead." to
        NomiTranslation(
            de = "Das aktuelle Gewicht bleibt bei {0} kg. Trage ein neues Gewicht stattdessen " +
                "unter Fortschritt ein.",
            es = "El peso actual se queda en {0} kg. Registra un peso nuevo desde Progreso.",
            fr = "Le poids actuel reste à {0} kg. Enregistre plutôt un nouveau poids depuis " +
                "Progression.",
            it = "Il peso attuale resta {0} kg. Registra un nuovo peso da Progressi.",
            nl = "Het huidige gewicht blijft {0} kg. Leg een nieuw gewicht vast via Voortgang.",
            pt = "O peso atual mantém-se em {0} kg. Regista um novo peso a partir de Progresso.",
            sq = "Pesha aktuale mbetet {0} kg. Regjistro një peshë të re te Përparimi.",
            sv = "Nuvarande vikt står kvar på {0} kg. Registrera en ny vikt under Utveckling i " +
                "stället.",
            tr = "Mevcut kilo {0} kg olarak kalır. Yeni kiloyu İlerleme sayfasından kaydet.",
        ),
    "Show {0} options" to NomiTranslation(
        de = "Optionen für {0} anzeigen", es = "Mostrar las opciones de {0}",
        fr = "Afficher les options de {0}", it = "Mostra le opzioni di {0}",
        nl = "Opties voor {0} tonen", pt = "Mostrar as opções de {0}",
        sq = "Shfaq opsionet e {0}", sv = "Visa alternativ för {0}",
        tr = "{0} seçeneklerini göster",
    ),

    // Nutrition plan editor
    "Nutrition targets" to NomiTranslation(
        de = "Ernährungsziele", es = "Objetivos nutricionales", fr = "Objectifs nutritionnels",
        it = "Obiettivi nutrizionali", nl = "Voedingsdoelen", pt = "Objetivos nutricionais",
        sq = "Objektivat ushqyese", sv = "Näringsmål", tr = "Beslenme hedefleri",
    ),
    "Set your daily targets" to NomiTranslation(
        de = "Lege deine Tagesziele fest", es = "Define tus objetivos diarios",
        fr = "Définis tes objectifs quotidiens", it = "Imposta i tuoi obiettivi giornalieri",
        nl = "Stel je dagelijkse doelen in", pt = "Define os teus objetivos diários",
        sq = "Cakto objektivat e tua ditore", sv = "Ange dina dagliga mål",
        tr = "Günlük hedeflerini belirle",
    ),
    "These values become the goals used by Today, History, and progress summaries." to
        NomiTranslation(
            de = "Diese Werte werden als Ziele für Heute, Verlauf und Fortschrittsübersichten " +
                "verwendet.",
            es = "Estos valores serán los objetivos que usan Hoy, el historial y los resúmenes " +
                "de progreso.",
            fr = "Ces valeurs deviennent les objectifs utilisés par Aujourd’hui, l’historique et " +
                "les résumés de progression.",
            it = "Questi valori diventano gli obiettivi usati da Oggi, dalla cronologia e dai " +
                "riepiloghi dei progressi.",
            nl = "Deze waarden worden de doelen die Vandaag, Geschiedenis en " +
                "voortgangsoverzichten gebruiken.",
            pt = "Estes valores passam a ser os objetivos usados por Hoje, pelo histórico e " +
                "pelos resumos de progresso.",
            sq = "Këto vlera bëhen objektivat që përdoren nga Sot, historiku dhe përmbledhjet e " +
                "përparimit.",
            sv = "De här värdena blir målen som Idag, Historik och utvecklingssammanfattningar " +
                "använder.",
            tr = "Bu değerler Bugün, Geçmiş ve ilerleme özetlerinin kullandığı hedefler olur.",
        ),
    "A new target version starts when you save" to NomiTranslation(
        de = "Beim Speichern beginnt eine neue Zielversion",
        es = "Al guardar comienza una nueva versión de objetivos",
        fr = "Une nouvelle version des objectifs démarre à l’enregistrement",
        it = "Al salvataggio inizia una nuova versione degli obiettivi",
        nl = "Bij opslaan begint een nieuwe doelversie",
        pt = "Ao guardar começa uma nova versão de objetivos",
        sq = "Kur ruan, nis një version i ri objektivash",
        sv = "En ny målversion startar när du sparar",
        tr = "Kaydettiğinde yeni bir hedef sürümü başlar",
    ),
    "Earlier days keep their original targets, so historical comparisons remain meaningful. All " +
        "values saved here are marked as custom." to NomiTranslation(
        de = "Frühere Tage behalten ihre ursprünglichen Ziele, damit historische Vergleiche " +
            "aussagekräftig bleiben. Alle hier gespeicherten Werte werden als benutzerdefiniert " +
            "markiert.",
        es = "Los días anteriores conservan sus objetivos originales, así que las comparaciones " +
            "históricas siguen teniendo sentido. Todos los valores guardados aquí se marcan como " +
            "personalizados.",
        fr = "Les journées passées conservent leurs objectifs d’origine, afin que les " +
            "comparaisons historiques restent pertinentes. Toutes les valeurs enregistrées ici " +
            "sont marquées comme personnalisées.",
        it = "I giorni precedenti mantengono gli obiettivi originali, così i confronti storici " +
            "restano significativi. Tutti i valori salvati qui sono contrassegnati come " +
            "personalizzati.",
        nl = "Eerdere dagen houden hun oorspronkelijke doelen, zodat historische vergelijkingen " +
            "zinvol blijven. Alle hier opgeslagen waarden worden als aangepast gemarkeerd.",
        pt = "Os dias anteriores mantêm os objetivos originais, para que as comparações " +
            "históricas continuem a fazer sentido. Todos os valores guardados aqui são marcados " +
            "como personalizados.",
        sq = "Ditët e mëparshme ruajnë objektivat origjinale, kështu që krahasimet historike " +
            "mbeten kuptimplota. Të gjitha vlerat e ruajtura këtu shënohen si të personalizuara.",
        sv = "Tidigare dagar behåller sina ursprungliga mål, så historiska jämförelser förblir " +
            "meningsfulla. Alla värden som sparas här markeras som egna.",
        tr = "Önceki günler özgün hedeflerini korur, böylece geçmiş karşılaştırmalar anlamlı " +
            "kalır. Burada kaydedilen tüm değerler özel olarak işaretlenir.",
    ),
    "Manual" to NomiTranslation(
        de = "Manuell", es = "Manual", fr = "Manuel", it = "Manuale", nl = "Handmatig",
        pt = "Manual", sq = "Manual", sv = "Manuell", tr = "Elle",
    ),
    "Custom" to NomiTranslation(
        de = "Benutzerdefiniert", es = "Personalizado", fr = "Personnalisé",
        it = "Personalizzato", nl = "Aangepast", pt = "Personalizado", sq = "I personalizuar",
        sv = "Egen", tr = "Özel",
    ),
    "Calculated" to NomiTranslation(
        de = "Berechnet", es = "Calculado", fr = "Calculé", it = "Calcolato", nl = "Berekend",
        pt = "Calculado", sq = "I llogaritur", sv = "Beräknad", tr = "Hesaplanan",
    ),
    "kcal/day" to NomiTranslation(
        de = "kcal/Tag", es = "kcal/día", fr = "kcal/jour", it = "kcal/giorno",
        nl = "kcal/dag", pt = "kcal/dia", sq = "kcal/ditë", sv = "kcal/dag", tr = "kcal/gün",
    ),
    "g/day" to NomiTranslation(
        de = "g/Tag", es = "g/día", fr = "g/jour", it = "g/giorno", nl = "g/dag",
        pt = "g/dia", sq = "g/ditë", sv = "g/dag", tr = "g/gün",
    ),
    "Macronutrients" to NomiTranslation(
        de = "Makronährstoffe", es = "Macronutrientes", fr = "Macronutriments",
        it = "Macronutrienti", nl = "Macronutriënten", pt = "Macronutrientes",
        sq = "Makronutrientët", sv = "Makronäringsämnen", tr = "Makro besinler",
    ),
    "Energy represented by macros" to NomiTranslation(
        de = "Durch Makros abgedeckte Energie", es = "Energía representada por los macros",
        fr = "Énergie représentée par les macros", it = "Energia rappresentata dai macro",
        nl = "Energie vertegenwoordigd door macro’s", pt = "Energia representada pelos macros",
        sq = "Energjia e përfaqësuar nga makrot", sv = "Energi från makronäringsämnena",
        tr = "Makroların karşıladığı enerji",
    ),
    "Protein and carbs use 4 kcal/g; fat uses 9 kcal/g." to NomiTranslation(
        de = "Eiweiß und Kohlenhydrate liefern 4 kcal/g, Fett liefert 9 kcal/g.",
        es = "Las proteínas y los carbohidratos aportan 4 kcal/g; las grasas, 9 kcal/g.",
        fr = "Les protéines et les glucides fournissent 4 kcal/g ; les lipides, 9 kcal/g.",
        it = "Proteine e carboidrati forniscono 4 kcal/g; i grassi 9 kcal/g.",
        nl = "Eiwitten en koolhydraten leveren 4 kcal/g; vetten 9 kcal/g.",
        pt = "As proteínas e os hidratos fornecem 4 kcal/g; as gorduras, 9 kcal/g.",
        sq = "Proteinat dhe karbohidratet japin 4 kcal/g; yndyrnat 9 kcal/g.",
        sv = "Protein och kolhydrater ger 4 kcal/g; fett ger 9 kcal/g.",
        tr = "Protein ve karbonhidrat 4 kcal/g, yağ 9 kcal/g sağlar.",
    ),
    "Check the highlighted targets." to NomiTranslation(
        de = "Prüfe die markierten Ziele.", es = "Revisa los objetivos marcados.",
        fr = "Vérifie les objectifs signalés.", it = "Controlla gli obiettivi evidenziati.",
        nl = "Controleer de gemarkeerde doelen.", pt = "Verifica os objetivos assinalados.",
        sq = "Kontrollo objektivat e theksuara.", sv = "Kontrollera de markerade målen.",
        tr = "İşaretli hedefleri kontrol et.",
    ),
    "Save custom targets" to NomiTranslation(
        de = "Benutzerdefinierte Ziele speichern", es = "Guardar objetivos personalizados",
        fr = "Enregistrer les objectifs personnalisés", it = "Salva gli obiettivi personalizzati",
        nl = "Aangepaste doelen opslaan", pt = "Guardar objetivos personalizados",
        sq = "Ruaj objektivat e personalizuara", sv = "Spara egna mål",
        tr = "Özel hedefleri kaydet",
    ),
    "Enter a calorie target." to NomiTranslation(
        de = "Gib ein Kalorienziel ein.", es = "Introduce un objetivo de calorías.",
        fr = "Saisis un objectif de calories.", it = "Inserisci un obiettivo di calorie.",
        nl = "Voer een caloriedoel in.", pt = "Introduz um objetivo de calorias.",
        sq = "Fut një objektiv kalorish.", sv = "Ange ett kalorimål.",
        tr = "Bir kalori hedefi gir.",
    ),
    "Calories must be between 800 and 6,000 kcal." to NomiTranslation(
        de = "Das Kalorienziel muss zwischen 800 und 6.000 kcal liegen.",
        es = "Las calorías deben estar entre 800 y 6000 kcal.",
        fr = "Les calories doivent être comprises entre 800 et 6 000 kcal.",
        it = "Le calorie devono essere comprese tra 800 e 6.000 kcal.",
        nl = "Calorieën moeten tussen 800 en 6.000 kcal liggen.",
        pt = "As calorias têm de estar entre 800 e 6000 kcal.",
        sq = "Kaloritë duhet të jenë midis 800 dhe 6.000 kcal.",
        sv = "Kalorierna måste vara mellan 800 och 6 000 kcal.",
        tr = "Kalori 800 ile 6.000 kcal arasında olmalı.",
    ),
    "Enter a protein target." to NomiTranslation(
        de = "Gib ein Eiweißziel ein.", es = "Introduce un objetivo de proteínas.",
        fr = "Saisis un objectif de protéines.", it = "Inserisci un obiettivo di proteine.",
        nl = "Voer een eiwitdoel in.", pt = "Introduz um objetivo de proteínas.",
        sq = "Fut një objektiv proteinash.", sv = "Ange ett proteinmål.",
        tr = "Bir protein hedefi gir.",
    ),
    "Protein must be between 0 and 600 g." to NomiTranslation(
        de = "Das Eiweißziel muss zwischen 0 und 600 g liegen.",
        es = "Las proteínas deben estar entre 0 y 600 g.",
        fr = "Les protéines doivent être comprises entre 0 et 600 g.",
        it = "Le proteine devono essere comprese tra 0 e 600 g.",
        nl = "Eiwitten moeten tussen 0 en 600 g liggen.",
        pt = "As proteínas têm de estar entre 0 e 600 g.",
        sq = "Proteinat duhet të jenë midis 0 dhe 600 g.",
        sv = "Protein måste vara mellan 0 och 600 g.",
        tr = "Protein 0 ile 600 g arasında olmalı.",
    ),
    "Enter a carbohydrate target." to NomiTranslation(
        de = "Gib ein Kohlenhydratziel ein.", es = "Introduce un objetivo de carbohidratos.",
        fr = "Saisis un objectif de glucides.", it = "Inserisci un obiettivo di carboidrati.",
        nl = "Voer een koolhydraatdoel in.", pt = "Introduz um objetivo de hidratos de carbono.",
        sq = "Fut një objektiv karbohidratesh.", sv = "Ange ett kolhydratmål.",
        tr = "Bir karbonhidrat hedefi gir.",
    ),
    "Carbohydrates must be between 0 and 900 g." to NomiTranslation(
        de = "Das Kohlenhydratziel muss zwischen 0 und 900 g liegen.",
        es = "Los carbohidratos deben estar entre 0 y 900 g.",
        fr = "Les glucides doivent être compris entre 0 et 900 g.",
        it = "I carboidrati devono essere compresi tra 0 e 900 g.",
        nl = "Koolhydraten moeten tussen 0 en 900 g liggen.",
        pt = "Os hidratos de carbono têm de estar entre 0 e 900 g.",
        sq = "Karbohidratet duhet të jenë midis 0 dhe 900 g.",
        sv = "Kolhydrater måste vara mellan 0 och 900 g.",
        tr = "Karbonhidrat 0 ile 900 g arasında olmalı.",
    ),
    "Enter a fat target." to NomiTranslation(
        de = "Gib ein Fettziel ein.", es = "Introduce un objetivo de grasas.",
        fr = "Saisis un objectif de lipides.", it = "Inserisci un obiettivo di grassi.",
        nl = "Voer een vetdoel in.", pt = "Introduz um objetivo de gorduras.",
        sq = "Fut një objektiv yndyrnash.", sv = "Ange ett fettmål.", tr = "Bir yağ hedefi gir.",
    ),
    "Fat must be between 0 and 300 g." to NomiTranslation(
        de = "Das Fettziel muss zwischen 0 und 300 g liegen.",
        es = "Las grasas deben estar entre 0 y 300 g.",
        fr = "Les lipides doivent être compris entre 0 et 300 g.",
        it = "I grassi devono essere compresi tra 0 e 300 g.",
        nl = "Vetten moeten tussen 0 en 300 g liggen.",
        pt = "As gorduras têm de estar entre 0 e 300 g.",
        sq = "Yndyrnat duhet të jenë midis 0 dhe 300 g.",
        sv = "Fett måste vara mellan 0 och 300 g.",
        tr = "Yağ 0 ile 300 g arasında olmalı.",
    ),
    "Plan version {0}" to NomiTranslation(
        de = "Planversion {0}", es = "Versión del plan {0}", fr = "Version du plan {0}",
        it = "Versione del piano {0}", nl = "Planversie {0}", pt = "Versão do plano {0}",
        sq = "Versioni i planit {0}", sv = "Planversion {0}", tr = "Plan sürümü {0}",
    ),
    "Effective {0} · {1}" to NomiTranslation(
        de = "Gültig ab {0} · {1}", es = "En vigor desde {0} · {1}",
        fr = "En vigueur le {0} · {1}", it = "In vigore dal {0} · {1}",
        nl = "Geldig vanaf {0} · {1}", pt = "Em vigor desde {0} · {1}",
        sq = "Në fuqi nga {0} · {1}", sv = "Gäller från {0} · {1}",
        tr = "{0} tarihinden geçerli · {1}",
    ),

    // Micronutrients
    "Micronutrients" to NomiTranslation(
        de = "Mikronährstoffe", es = "Micronutrientes", fr = "Micronutriments",
        it = "Micronutrienti", nl = "Micronutriënten", pt = "Micronutrientes",
        sq = "Mikronutrientët", sv = "Mikronäringsämnen", tr = "Mikro besinler",
    ),
    "Track more than macros" to NomiTranslation(
        de = "Mehr als nur Makros verfolgen", es = "Controla algo más que los macros",
        fr = "Suis plus que les macros", it = "Monitora più dei soli macro",
        nl = "Houd meer bij dan macro’s", pt = "Acompanha mais do que macros",
        sq = "Ndiq më shumë se makrot", sv = "Följ mer än makrona",
        tr = "Makroların ötesini izle",
    ),
    "Turn on only what you care about. Each one you enable appears on Today with its own daily " +
        "goal." to NomiTranslation(
        de = "Aktiviere nur, was dich interessiert. Jeder aktivierte Nährstoff erscheint mit " +
            "eigenem Tagesziel auf Heute.",
        es = "Activa solo lo que te interese. Cada nutriente que actives aparece en Hoy con su " +
            "propio objetivo diario.",
        fr = "N’active que ce qui t’intéresse. Chaque nutriment activé apparaît dans " +
            "Aujourd’hui avec son propre objectif quotidien.",
        it = "Attiva solo ciò che ti interessa. Ogni nutriente attivato compare in Oggi con il " +
            "proprio obiettivo giornaliero.",
        nl = "Zet alleen aan wat je interesseert. Elk ingeschakeld nutriënt verschijnt bij " +
            "Vandaag met een eigen dagdoel.",
        pt = "Ativa só o que te interessa. Cada nutriente ativado aparece em Hoje com o seu " +
            "próprio objetivo diário.",
        sq = "Aktivizo vetëm atë që të intereson. Çdo lëndë e aktivizuar shfaqet te Sot me " +
            "objektivin e vet ditor.",
        sv = "Slå bara på det du bryr dig om. Varje aktiverat näringsämne visas under Idag med " +
            "ett eget dagsmål.",
        tr = "Yalnızca önemsediklerini aç. Açtığın her besin, kendi günlük hedefiyle Bugün " +
            "sayfasında görünür.",
    ),
    "Your existing days count too" to NomiTranslation(
        de = "Deine bisherigen Tage zählen mit", es = "Tus días anteriores también cuentan",
        fr = "Tes journées passées comptent aussi", it = "Contano anche le giornate già registrate",
        nl = "Je eerdere dagen tellen ook mee", pt = "Os teus dias anteriores também contam",
        sq = "Edhe ditët e tua ekzistuese llogariten", sv = "Dina tidigare dagar räknas också",
        tr = "Önceki günlerin de sayılır",
    ),
    "Nomi has been storing these values alongside every food you logged, so a nutrient you " +
        "enable today already has history behind it. Foods whose source never published a value " +
        "are left out of the total rather than counted as zero." to NomiTranslation(
        de = "Nomi speichert diese Werte bereits zu jedem erfassten Lebensmittel. Ein heute " +
            "aktivierter Nährstoff hat also schon Verlauf. Lebensmittel, für die keine Quelle " +
            "einen Wert veröffentlicht hat, fließen nicht als Null in die Summe ein.",
        es = "Nomi ya guardaba estos valores junto a cada alimento registrado, así que un " +
            "nutriente que actives hoy ya tiene historial. Los alimentos cuya fuente nunca " +
            "publicó un valor quedan fuera del total en vez de contar como cero.",
        fr = "Nomi enregistre déjà ces valeurs avec chaque aliment consigné : un nutriment " +
            "activé aujourd’hui a donc déjà un historique. Les aliments dont la source n’a " +
            "jamais publié de valeur sont exclus du total plutôt que comptés comme zéro.",
        it = "Nomi salva già questi valori insieme a ogni alimento registrato, quindi un " +
            "nutriente attivato oggi ha già uno storico. Gli alimenti la cui fonte non ha mai " +
            "pubblicato un valore restano fuori dal totale invece di contare come zero.",
        nl = "Nomi bewaart deze waarden al bij elk vastgelegd product, dus een nutriënt dat je " +
            "vandaag inschakelt heeft al historie. Producten waarvan de bron nooit een waarde " +
            "publiceerde blijven buiten het totaal in plaats van als nul te tellen.",
        pt = "A Nomi já guardava estes valores junto de cada alimento registado, por isso um " +
            "nutriente que ativares hoje já tem histórico. Os alimentos cuja fonte nunca " +
            "publicou um valor ficam de fora do total em vez de contarem como zero.",
        sq = "Nomi i ka ruajtur këto vlera bashkë me çdo ushqim që ke regjistruar, prandaj një " +
            "lëndë që aktivizon sot ka tashmë histori. Ushqimet burimi i të cilave nuk ka " +
            "publikuar kurrë një vlerë lihen jashtë totalit në vend që të numërohen si zero.",
        sv = "Nomi har redan sparat de här värdena tillsammans med varje loggat livsmedel, så " +
            "ett näringsämne du slår på idag har redan historik. Livsmedel vars källa aldrig " +
            "publicerat ett värde utelämnas från summan i stället för att räknas som noll.",
        tr = "Nomi bu değerleri kaydettiğin her besinle birlikte zaten saklıyordu, bu yüzden " +
            "bugün açtığın bir besin öğesinin geçmişi hazır. Kaynağı hiç değer yayımlamamış " +
            "besinler sıfır sayılmak yerine toplamın dışında bırakılır.",
    ),
    "Daily target" to NomiTranslation(
        de = "Tagesziel", es = "Objetivo diario", fr = "Objectif quotidien",
        it = "Obiettivo giornaliero", nl = "Dagdoel", pt = "Objetivo diário",
        sq = "Objektivi ditor", sv = "Dagsmål", tr = "Günlük hedef",
    ),
    "Enter a daily target." to NomiTranslation(
        de = "Gib ein Tagesziel ein.", es = "Introduce un objetivo diario.",
        fr = "Saisis un objectif quotidien.", it = "Inserisci un obiettivo giornaliero.",
        nl = "Voer een dagdoel in.", pt = "Introduz um objetivo diário.",
        sq = "Fut një objektiv ditor.", sv = "Ange ett dagsmål.", tr = "Bir günlük hedef gir.",
    ),
    "That target is outside the supported range." to NomiTranslation(
        de = "Dieses Ziel liegt außerhalb des unterstützten Bereichs.",
        es = "Ese objetivo está fuera del rango admitido.",
        fr = "Cet objectif est en dehors de la plage prise en charge.",
        it = "Questo obiettivo è fuori dall’intervallo supportato.",
        nl = "Dat doel valt buiten het ondersteunde bereik.",
        pt = "Esse objetivo está fora do intervalo suportado.",
        sq = "Ai objektiv është jashtë intervalit të mbështetur.",
        sv = "Målet ligger utanför det intervall som stöds.",
        tr = "Bu hedef desteklenen aralığın dışında.",
    ),
    "Fiber" to NomiTranslation(
        de = "Ballaststoffe", es = "Fibra", fr = "Fibres", it = "Fibre", nl = "Vezels",
        pt = "Fibra", sq = "Fibra", sv = "Fibrer", tr = "Lif",
    ),
    "Sugar" to NomiTranslation(
        de = "Zucker", es = "Azúcares", fr = "Sucres", it = "Zuccheri", nl = "Suikers",
        pt = "Açúcares", sq = "Sheqerna", sv = "Socker", tr = "Şeker",
    ),
    "Saturated fat" to NomiTranslation(
        de = "Gesättigte Fettsäuren", es = "Grasas saturadas", fr = "Acides gras saturés",
        it = "Grassi saturi", nl = "Verzadigd vet", pt = "Gorduras saturadas",
        sq = "Yndyrna të ngopura", sv = "Mättat fett", tr = "Doymuş yağ",
    ),
    "Sodium" to NomiTranslation(
        de = "Natrium", es = "Sodio", fr = "Sodium", it = "Sodio", nl = "Natrium",
        pt = "Sódio", sq = "Natrium", sv = "Natrium", tr = "Sodyum",
    ),
    "{0}/day" to NomiTranslation(
        de = "{0}/Tag", es = "{0}/día", fr = "{0}/jour", it = "{0}/giorno", nl = "{0}/dag",
        pt = "{0}/dia", sq = "{0}/ditë", sv = "{0}/dag", tr = "{0}/gün",
    ),
    "Aim for at least {0} a day. Most people get well under half of that." to NomiTranslation(
        de = "Ziel: mindestens {0} pro Tag. Die meisten erreichen nicht einmal die Hälfte.",
        es = "Intenta llegar al menos a {0} al día. La mayoría no alcanza ni la mitad.",
        fr = "Vise au moins {0} par jour. La plupart des gens n’en atteignent pas la moitié.",
        it = "Punta ad almeno {0} al giorno. Molti non arrivano nemmeno alla metà.",
        nl = "Streef naar minstens {0} per dag. De meeste mensen halen nog niet de helft.",
        pt = "Tenta chegar a pelo menos {0} por dia. A maioria não atinge metade disso.",
        sq = "Synoje të paktën {0} në ditë. Shumica nuk arrijnë as gjysmën.",
        sv = "Sikta på minst {0} per dag. De flesta får i sig långt under hälften.",
        tr = "Günde en az {0} hedefle. Çoğu kişi bunun yarısına bile ulaşmaz.",
    ),
    "Keep added and free sugars under {0} a day. Drinks are where it adds up fastest." to
        NomiTranslation(
            de = "Halte zugesetzten und freien Zucker unter {0} pro Tag. Am schnellsten summieren " +
                "sich Getränke.",
            es = "Mantén los azúcares añadidos y libres por debajo de {0} al día. Donde más " +
                "rápido se acumulan es en las bebidas.",
            fr = "Reste sous {0} de sucres ajoutés et libres par jour. C’est dans les boissons " +
                "que cela monte le plus vite.",
            it = "Mantieni gli zuccheri aggiunti e liberi sotto {0} al giorno. Sono le bevande a " +
                "farli salire più in fretta.",
            nl = "Houd toegevoegde en vrije suikers onder {0} per dag. In drankjes loopt het het " +
                "snelst op.",
            pt = "Mantém os açúcares adicionados e livres abaixo de {0} por dia. É nas bebidas " +
                "que somam mais depressa.",
            sq = "Mbaji sheqernat e shtuar dhe të lirë nën {0} në ditë. Pijet i shtojnë më shpejt.",
            sv = "Håll tillsatt och fritt socker under {0} per dag. Det är i drycker det ökar " +
                "snabbast.",
            tr = "Eklenmiş ve serbest şekeri günde {0} altında tut. En hızlı içeceklerle birikir.",
        ),
    "Stay under about {0} a day, roughly a tenth of a 2,000 kcal day." to NomiTranslation(
        de = "Bleib unter etwa {0} pro Tag, ungefähr ein Zehntel eines 2.000-kcal-Tages.",
        es = "No superes unos {0} al día, aproximadamente una décima parte de un día de 2000 kcal.",
        fr = "Reste sous environ {0} par jour, soit près d’un dixième d’une journée à 2 000 kcal.",
        it = "Resta sotto circa {0} al giorno, all’incirca un decimo di una giornata da 2.000 kcal.",
        nl = "Blijf onder ongeveer {0} per dag, ruwweg een tiende van een dag van 2.000 kcal.",
        pt = "Fica abaixo de cerca de {0} por dia, sensivelmente um décimo de um dia de 2000 kcal.",
        sq = "Qëndro nën rreth {0} në ditë, afërsisht një e dhjeta e një dite me 2.000 kcal.",
        sv = "Håll dig under ungefär {0} per dag, cirka en tiondel av en dag på 2 000 kcal.",
        tr = "Günde yaklaşık {0} altında kal; bu, 2.000 kcal’lik bir günün kabaca onda biri.",
    ),
    "Stay under {0} a day, which is about 5 g of salt." to NomiTranslation(
        de = "Bleib unter {0} pro Tag, das entspricht etwa 5 g Salz.",
        es = "No superes {0} al día, que equivale a unos 5 g de sal.",
        fr = "Reste sous {0} par jour, soit environ 5 g de sel.",
        it = "Resta sotto {0} al giorno, che corrisponde a circa 5 g di sale.",
        nl = "Blijf onder {0} per dag, dat is ongeveer 5 g zout.",
        pt = "Fica abaixo de {0} por dia, o que corresponde a cerca de 5 g de sal.",
        sq = "Qëndro nën {0} në ditë, që është rreth 5 g kripë.",
        sv = "Håll dig under {0} per dag, vilket motsvarar ungefär 5 g salt.",
        tr = "Günde {0} altında kal; bu yaklaşık 5 g tuza denk gelir.",
    ),

    // Progress
    "7 days" to NomiTranslation(
        de = "7 Tage", es = "7 días", fr = "7 jours", it = "7 giorni", nl = "7 dagen",
        pt = "7 dias", sq = "7 ditë", sv = "7 dagar", tr = "7 gün",
    ),
    "30 days" to NomiTranslation(
        de = "30 Tage", es = "30 días", fr = "30 jours", it = "30 giorni", nl = "30 dagen",
        pt = "30 dias", sq = "30 ditë", sv = "30 dagar", tr = "30 gün",
    ),
    "3 months" to NomiTranslation(
        de = "3 Monate", es = "3 meses", fr = "3 mois", it = "3 mesi", nl = "3 maanden",
        pt = "3 meses", sq = "3 muaj", sv = "3 månader", tr = "3 ay",
    ),
    "6 months" to NomiTranslation(
        de = "6 Monate", es = "6 meses", fr = "6 mois", it = "6 mesi", nl = "6 maanden",
        pt = "6 meses", sq = "6 muaj", sv = "6 månader", tr = "6 ay",
    ),
    "1 year" to NomiTranslation(
        de = "1 Jahr", es = "1 año", fr = "1 an", it = "1 anno", nl = "1 jaar",
        pt = "1 ano", sq = "1 vit", sv = "1 år", tr = "1 yıl",
    ),
    "All" to NomiTranslation(
        de = "Alle", es = "Todo", fr = "Tout", it = "Tutto", nl = "Alles", pt = "Tudo",
        sq = "Të gjitha", sv = "Allt", tr = "Tümü",
    ),
    "Weight" to NomiTranslation(
        de = "Gewicht", es = "Peso", fr = "Poids", it = "Peso", nl = "Gewicht",
        pt = "Peso", sq = "Pesha", sv = "Vikt", tr = "Kilo",
    ),
    "Add" to NomiTranslation(
        de = "Hinzufügen", es = "Añadir", fr = "Ajouter", it = "Aggiungi", nl = "Toevoegen",
        pt = "Adicionar", sq = "Shto", sv = "Lägg till", tr = "Ekle",
    ),
    "Starting" to NomiTranslation(
        de = "Start", es = "Inicial", fr = "Départ", it = "Iniziale", nl = "Start",
        pt = "Inicial", sq = "Fillimi", sv = "Start", tr = "Başlangıç",
    ),
    "Current" to NomiTranslation(
        de = "Aktuell", es = "Actual", fr = "Actuel", it = "Attuale", nl = "Huidig",
        pt = "Atual", sq = "Aktuale", sv = "Aktuell", tr = "Güncel",
    ),
    "Log a little more to see your weight trend." to NomiTranslation(
        de = "Trage noch etwas mehr ein, um deinen Gewichtsverlauf zu sehen.",
        es = "Registra un poco más para ver tu tendencia de peso.",
        fr = "Enregistre encore un peu pour voir ta tendance de poids.",
        it = "Registra ancora un po’ per vedere l’andamento del peso.",
        nl = "Leg wat meer vast om je gewichtstrend te zien.",
        pt = "Regista mais um pouco para veres a tua tendência de peso.",
        sq = "Regjistro edhe pak për të parë tendencën e peshës.",
        sv = "Logga lite mer för att se din viktkurva.",
        tr = "Kilo eğilimini görmek için biraz daha kayıt gir.",
    ),
    "Consistency" to NomiTranslation(
        de = "Regelmäßigkeit", es = "Constancia", fr = "Régularité", it = "Costanza",
        nl = "Regelmaat", pt = "Consistência", sq = "Qëndrueshmëria", sv = "Regelbundenhet",
        tr = "Düzenlilik",
    ),
    "Daily averages" to NomiTranslation(
        de = "Tagesdurchschnitt", es = "Medias diarias", fr = "Moyennes quotidiennes",
        it = "Medie giornaliere", nl = "Daggemiddelden", pt = "Médias diárias",
        sq = "Mesataret ditore", sv = "Dagsgenomsnitt", tr = "Günlük ortalamalar",
    ),
    "{0} of {1} days logged" to NomiTranslation(
        de = "An {0} von {1} Tagen eingetragen", es = "{0} de {1} días registrados",
        fr = "{0} jours sur {1} enregistrés", it = "{0} di {1} giorni registrati",
        nl = "{0} van {1} dagen vastgelegd", pt = "{0} de {1} dias registados",
        sq = "{0} nga {1} ditë të regjistruara", sv = "{0} av {1} dagar loggade",
        tr = "{1} günün {0} tanesi kaydedildi",
    ),
    "Weight trend from {0} to {1} kilograms across {2} measurements" to NomiTranslation(
        de = "Gewichtsverlauf von {0} bis {1} Kilogramm über {2} Messungen",
        es = "Tendencia de peso de {0} a {1} kilogramos a lo largo de {2} mediciones",
        fr = "Tendance de poids de {0} à {1} kilogrammes sur {2} mesures",
        it = "Andamento del peso da {0} a {1} chilogrammi su {2} misurazioni",
        nl = "Gewichtstrend van {0} naar {1} kilogram over {2} metingen",
        pt = "Tendência de peso de {0} para {1} quilogramas ao longo de {2} medições",
        sq = "Tendenca e peshës nga {0} në {1} kilogramë përgjatë {2} matjeve",
        sv = "Viktkurva från {0} till {1} kilogram över {2} mätningar",
        tr = "{2} ölçüm boyunca {0} kilogramdan {1} kilograma kilo eğilimi",
    ),
)
