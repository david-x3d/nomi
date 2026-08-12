package com.nomi.app.ui.localization

/** The nutrition detail sheet, the food logging flow and the portion editor. */
internal val foodTranslations: Map<String, NomiTranslation> = mapOf(
    // Meal categories
    "Breakfast" to NomiTranslation(
        de = "Frühstück", es = "Desayuno", fr = "Petit-déjeuner", it = "Colazione",
        nl = "Ontbijt", pt = "Pequeno-almoço", sq = "Mëngjes", sv = "Frukost", tr = "Kahvaltı",
    ),
    "Lunch" to NomiTranslation(
        de = "Mittagessen", es = "Almuerzo", fr = "Déjeuner", it = "Pranzo", nl = "Lunch",
        pt = "Almoço", sq = "Drekë", sv = "Lunch", tr = "Öğle yemeği",
    ),
    "Dinner" to NomiTranslation(
        de = "Abendessen", es = "Cena", fr = "Dîner", it = "Cena", nl = "Avondeten",
        pt = "Jantar", sq = "Darkë", sv = "Middag", tr = "Akşam yemeği",
    ),
    "Snacks" to NomiTranslation(
        de = "Snacks", es = "Tentempiés", fr = "En-cas", it = "Spuntini", nl = "Tussendoortjes",
        pt = "Lanches", sq = "Ushqime të lehta", sv = "Mellanmål", tr = "Atıştırmalıklar",
    ),

    // Macros
    "Protein" to NomiTranslation(
        de = "Eiweiß", es = "Proteínas", fr = "Protéines", it = "Proteine", nl = "Eiwitten",
        pt = "Proteínas", sq = "Proteina", sv = "Protein", tr = "Protein",
    ),
    "Carbs" to NomiTranslation(
        de = "Kohlenhydrate", es = "Carbohidratos", fr = "Glucides", it = "Carboidrati",
        nl = "Koolhydraten", pt = "Hidratos", sq = "Karbohidrate", sv = "Kolhydrater",
        tr = "Karbonhidrat",
    ),
    "Fat" to NomiTranslation(
        de = "Fett", es = "Grasas", fr = "Lipides", it = "Grassi", nl = "Vetten",
        pt = "Gorduras", sq = "Yndyrna", sv = "Fett", tr = "Yağ",
    ),

    // Nutrition detail sheet
    "Nutrition details" to NomiTranslation(
        de = "Ernährungsdetails", es = "Detalles nutricionales", fr = "Détails nutritionnels",
        it = "Dettagli nutrizionali", nl = "Voedingsdetails", pt = "Detalhes nutricionais",
        sq = "Detajet ushqyese", sv = "Näringsdetaljer", tr = "Besin ayrıntıları",
    ),
    "Close nutrition details" to NomiTranslation(
        de = "Ernährungsdetails schließen", es = "Cerrar los detalles nutricionales",
        fr = "Fermer les détails nutritionnels", it = "Chiudi i dettagli nutrizionali",
        nl = "Voedingsdetails sluiten", pt = "Fechar os detalhes nutricionais",
        sq = "Mbyll detajet ushqyese", sv = "Stäng näringsdetaljer",
        tr = "Besin ayrıntılarını kapat",
    ),
    "Correct the eaten amount" to NomiTranslation(
        de = "Gegessene Menge korrigieren", es = "Corregir la cantidad consumida",
        fr = "Corriger la quantité mangée", it = "Correggi la quantità mangiata",
        nl = "Gegeten hoeveelheid corrigeren", pt = "Corrigir a quantidade consumida",
        sq = "Korrigjo sasinë e ngrënë", sv = "Rätta mängden du åt",
        tr = "Yenen miktarı düzelt",
    ),
    "Food entry actions" to NomiTranslation(
        de = "Aktionen für den Lebensmitteleintrag", es = "Acciones de la entrada de comida",
        fr = "Actions sur l’entrée alimentaire", it = "Azioni sulla voce alimentare",
        nl = "Acties voor het voedingsitem", pt = "Ações da entrada de alimento",
        sq = "Veprime për regjistrimin e ushqimit", sv = "Åtgärder för livsmedelsposten",
        tr = "Besin kaydı işlemleri",
    ),
    "Entry actions" to NomiTranslation(
        de = "Aktionen", es = "Acciones", fr = "Actions", it = "Azioni", nl = "Acties",
        pt = "Ações", sq = "Veprime", sv = "Åtgärder", tr = "İşlemler",
    ),
    "Change amount" to NomiTranslation(
        de = "Menge ändern", es = "Cambiar la cantidad", fr = "Modifier la quantité",
        it = "Cambia la quantità", nl = "Hoeveelheid wijzigen", pt = "Alterar a quantidade",
        sq = "Ndrysho sasinë", sv = "Ändra mängd", tr = "Miktarı değiştir",
    ),
    "Recalculate this entry if you ate more or less" to NomiTranslation(
        de = "Neu berechnen, wenn du mehr oder weniger gegessen hast",
        es = "Recalcula esta entrada si comiste más o menos",
        fr = "Recalcule cette entrée si tu as mangé plus ou moins",
        it = "Ricalcola questa voce se hai mangiato di più o di meno",
        nl = "Herbereken dit item als je meer of minder at",
        pt = "Recalcula esta entrada se comeste mais ou menos",
        sq = "Rillogarit këtë regjistrim nëse hëngre më shumë ose më pak",
        sv = "Räkna om posten om du åt mer eller mindre",
        tr = "Daha çok veya daha az yediysen bu kaydı yeniden hesapla",
    ),
    "Add to favorites" to NomiTranslation(
        de = "Zu Favoriten hinzufügen", es = "Añadir a favoritos", fr = "Ajouter aux favoris",
        it = "Aggiungi ai preferiti", nl = "Aan favorieten toevoegen",
        pt = "Adicionar aos favoritos", sq = "Shto te të preferuarat",
        sv = "Lägg till i favoriter", tr = "Favorilere ekle",
    ),
    "Keep this food ready for faster logging" to NomiTranslation(
        de = "Dieses Lebensmittel schneller wieder eintragen",
        es = "Ten este alimento a mano para registrarlo más rápido",
        fr = "Garde cet aliment sous la main pour l’enregistrer plus vite",
        it = "Tieni questo alimento a portata di mano per registrarlo più in fretta",
        nl = "Houd dit product bij de hand om sneller vast te leggen",
        pt = "Mantém este alimento à mão para registares mais depressa",
        sq = "Mbaje këtë ushqim gati për regjistrim më të shpejtë",
        sv = "Ha livsmedlet redo för snabbare loggning",
        tr = "Daha hızlı kaydetmek için bu besini hazır tut",
    ),
    "Available after this food is saved to your library" to NomiTranslation(
        de = "Verfügbar, sobald das Lebensmittel gespeichert ist",
        es = "Disponible cuando este alimento se guarde en tu biblioteca",
        fr = "Disponible une fois cet aliment enregistré dans ta bibliothèque",
        it = "Disponibile dopo aver salvato questo alimento nella libreria",
        nl = "Beschikbaar zodra dit product in je bibliotheek staat",
        pt = "Disponível depois de este alimento ser guardado na tua biblioteca",
        sq = "I disponueshëm pasi ky ushqim të ruhet në bibliotekën tënde",
        sv = "Tillgängligt när livsmedlet sparats i ditt bibliotek",
        tr = "Bu besin kitaplığına kaydedildikten sonra kullanılabilir",
    ),
    "Duplicate entry" to NomiTranslation(
        de = "Eintrag duplizieren", es = "Duplicar la entrada", fr = "Dupliquer l’entrée",
        it = "Duplica la voce", nl = "Item dupliceren", pt = "Duplicar a entrada",
        sq = "Dyfisho regjistrimin", sv = "Duplicera posten", tr = "Kaydı çoğalt",
    ),
    "Add another copy to this day" to NomiTranslation(
        de = "Eine weitere Portion für diesen Tag eintragen",
        es = "Añade otra copia a este día",
        fr = "Ajoute une autre copie à cette journée",
        it = "Aggiungi un’altra copia a questa giornata",
        nl = "Voeg nog een kopie toe aan deze dag",
        pt = "Adiciona outra cópia a este dia",
        sq = "Shto një kopje tjetër në këtë ditë",
        sv = "Lägg till en kopia till den här dagen",
        tr = "Bu güne bir kopya daha ekle",
    ),
    "Delete entry" to NomiTranslation(
        de = "Eintrag löschen", es = "Eliminar la entrada", fr = "Supprimer l’entrée",
        it = "Elimina la voce", nl = "Item verwijderen", pt = "Eliminar a entrada",
        sq = "Fshi regjistrimin", sv = "Radera posten", tr = "Kaydı sil",
    ),
    "Remove it from this day" to NomiTranslation(
        de = "Aus diesem Tag entfernen", es = "Quítalo de este día",
        fr = "Le retirer de cette journée", it = "Rimuovilo da questa giornata",
        nl = "Verwijder het uit deze dag", pt = "Remove-o deste dia",
        sq = "Hiqe nga kjo ditë", sv = "Ta bort den från dagen", tr = "Bu günden kaldır",
    ),
    "Loading nutrition…" to NomiTranslation(
        de = "Nährwerte werden geladen…", es = "Cargando la información nutricional…",
        fr = "Chargement des valeurs nutritionnelles…", it = "Caricamento dei valori nutrizionali…",
        nl = "Voedingswaarde laden…", pt = "A carregar os valores nutricionais…",
        sq = "Po ngarkohen vlerat ushqyese…", sv = "Laddar näringsvärden…",
        tr = "Besin değerleri yükleniyor…",
    ),
    "Estimated" to NomiTranslation(
        de = "Geschätzt", es = "Estimado", fr = "Estimé", it = "Stimato", nl = "Geschat",
        pt = "Estimado", sq = "E vlerësuar", sv = "Uppskattat", tr = "Tahmini",
    ),
    "Nomi estimate" to NomiTranslation(
        de = "Nomi-Schätzung", es = "Estimación de Nomi", fr = "Estimation de Nomi",
        it = "Stima di Nomi", nl = "Schatting van Nomi", pt = "Estimativa da Nomi",
        sq = "Vlerësim i Nomit", sv = "Nomis uppskattning", tr = "Nomi tahmini",
    ),
    "Manually logged" to NomiTranslation(
        de = "Manuell eingetragen", es = "Registrado manualmente", fr = "Saisi manuellement",
        it = "Registrato manualmente", nl = "Handmatig vastgelegd", pt = "Registado manualmente",
        sq = "Regjistruar manualisht", sv = "Manuellt loggat", tr = "Elle kaydedildi",
    ),
    "Items & source" to NomiTranslation(
        de = "Eintrag & Quelle", es = "Elementos y fuente", fr = "Éléments et source",
        it = "Voci e fonte", nl = "Items en bron", pt = "Itens e fonte",
        sq = "Artikujt dhe burimi", sv = "Poster och källa", tr = "Öğeler ve kaynak",
    ),
    "Collapse item details" to NomiTranslation(
        de = "Eintragsdetails einklappen", es = "Contraer los detalles del elemento",
        fr = "Réduire les détails de l’élément", it = "Comprimi i dettagli della voce",
        nl = "Itemdetails inklappen", pt = "Recolher os detalhes do item",
        sq = "Palos detajet e artikullit", sv = "Dölj postens detaljer",
        tr = "Öğe ayrıntılarını daralt",
    ),
    "Expand item details" to NomiTranslation(
        de = "Eintragsdetails ausklappen", es = "Ampliar los detalles del elemento",
        fr = "Développer les détails de l’élément", it = "Espandi i dettagli della voce",
        nl = "Itemdetails uitklappen", pt = "Expandir os detalhes do item",
        sq = "Zgjero detajet e artikullit", sv = "Visa postens detaljer",
        tr = "Öğe ayrıntılarını genişlet",
    ),
    "Nutrition source" to NomiTranslation(
        de = "Nährwertquelle", es = "Fuente nutricional", fr = "Source nutritionnelle",
        it = "Fonte dei valori nutrizionali", nl = "Voedingsbron", pt = "Fonte nutricional",
        sq = "Burimi i vlerave ushqyese", sv = "Näringskälla", tr = "Besin değeri kaynağı",
    ),
    "the food description you entered" to NomiTranslation(
        de = "deiner eingegebenen Lebensmittelbeschreibung",
        es = "la descripción del alimento que escribiste",
        fr = "la description de l’aliment que tu as saisie",
        it = "la descrizione dell’alimento che hai inserito",
        nl = "de omschrijving die je hebt ingevoerd",
        pt = "a descrição do alimento que introduziste",
        sq = "përshkrimin e ushqimit që shkrove",
        sv = "livsmedelsbeskrivningen du angav",
        tr = "girdiğin besin açıklaması",
    ),
    "the saved nutrition values" to NomiTranslation(
        de = "den gespeicherten Nährwerten", es = "los valores nutricionales guardados",
        fr = "les valeurs nutritionnelles enregistrées", it = "i valori nutrizionali salvati",
        nl = "de opgeslagen voedingswaarden", pt = "os valores nutricionais guardados",
        sq = "vlerat ushqyese të ruajtura", sv = "de sparade näringsvärdena",
        tr = "kaydedilmiş besin değerleri",
    ),
    "Nomi’s thought process" to NomiTranslation(
        de = "Nomis Gedankengang", es = "El razonamiento de Nomi",
        fr = "Le raisonnement de Nomi", it = "Il ragionamento di Nomi",
        nl = "De redenering van Nomi", pt = "O raciocínio da Nomi",
        sq = "Arsyetimi i Nomit", sv = "Nomis resonemang", tr = "Nomi’nin düşünce süreci",
    ),
    "Estimate summary" to NomiTranslation(
        de = "Zusammenfassung der Schätzung", es = "Resumen de la estimación",
        fr = "Résumé de l’estimation", it = "Riepilogo della stima",
        nl = "Samenvatting van de schatting", pt = "Resumo da estimativa",
        sq = "Përmbledhje e vlerësimit", sv = "Sammanfattning av uppskattningen",
        tr = "Tahmin özeti",
    ),
    "Nutrition summary" to NomiTranslation(
        de = "Nährwertübersicht", es = "Resumen nutricional", fr = "Résumé nutritionnel",
        it = "Riepilogo nutrizionale", nl = "Voedingsoverzicht", pt = "Resumo nutricional",
        sq = "Përmbledhje ushqyese", sv = "Näringssammanfattning", tr = "Besin özeti",
    ),
    "Confidence level" to NomiTranslation(
        de = "Konfidenzniveau", es = "Nivel de confianza", fr = "Niveau de confiance",
        it = "Livello di affidabilità", nl = "Betrouwbaarheidsniveau", pt = "Nível de confiança",
        sq = "Niveli i besueshmërisë", sv = "Tillförlitlighetsnivå", tr = "Güven düzeyi",
    ),
    "Very high" to NomiTranslation(
        de = "Sehr hoch", es = "Muy alto", fr = "Très élevé", it = "Molto alta",
        nl = "Zeer hoog", pt = "Muito alto", sq = "Shumë i lartë", sv = "Mycket hög",
        tr = "Çok yüksek",
    ),
    "Low" to NomiTranslation(
        de = "Niedrig", es = "Bajo", fr = "Faible", it = "Bassa", nl = "Laag",
        pt = "Baixo", sq = "I ulët", sv = "Låg", tr = "Düşük",
    ),
    "Something off? Tap to edit" to NomiTranslation(
        de = "Stimmt etwas nicht? Zum Bearbeiten tippen",
        es = "¿Algo no cuadra? Toca para editarlo",
        fr = "Quelque chose cloche ? Touche pour modifier",
        it = "Qualcosa non torna? Tocca per modificare",
        nl = "Klopt er iets niet? Tik om te bewerken",
        pt = "Algo não bate certo? Toca para editar",
        sq = "Diçka nuk përputhet? Prek për ta ndryshuar",
        sv = "Stämmer något inte? Tryck för att ändra",
        tr = "Bir şey mi yanlış? Düzenlemek için dokun",
    ),
    "Confidence {0} out of 100" to NomiTranslation(
        de = "Konfidenz {0} von 100", es = "Confianza {0} sobre 100",
        fr = "Confiance {0} sur 100", it = "Affidabilità {0} su 100",
        nl = "Betrouwbaarheid {0} van 100", pt = "Confiança {0} em 100",
        sq = "Besueshmëri {0} nga 100", sv = "Tillförlitlighet {0} av 100",
        tr = "100 üzerinden {0} güven",
    ),

    // What the cited page itself said
    "Mainly checked" to NomiTranslation(
        de = "Hauptsächlich geprüft", es = "Consultado sobre todo",
        fr = "Surtout consulté", it = "Consultato soprattutto",
        nl = "Vooral bekeken", pt = "Consultado sobretudo",
        sq = "Kryesisht i kontrolluar", sv = "Främst kontrollerad",
        tr = "Ağırlıklı olarak incelenen",
    ),
    "Product on the page" to NomiTranslation(
        de = "Produkt auf der Seite", es = "Producto en la página",
        fr = "Produit sur la page", it = "Prodotto sulla pagina",
        nl = "Product op de pagina", pt = "Produto na página",
        sq = "Produkti në faqe", sv = "Produkt på sidan", tr = "Sayfadaki ürün",
    ),
    "Values are per" to NomiTranslation(
        de = "Werte gelten für", es = "Valores por", fr = "Valeurs pour",
        it = "Valori per", nl = "Waarden per", pt = "Valores por",
        sq = "Vlerat për", sv = "Värden per", tr = "Değerler şu miktar için",
    ),
    "Nomi worked this out from your description rather than from a published table." to
        NomiTranslation(
            de = "Nomi hat das aus deiner Beschreibung abgeleitet, nicht aus einer " +
                "veröffentlichten Tabelle.",
            es = "Nomi lo ha deducido de tu descripción, no de una tabla publicada.",
            fr = "Nomi a déduit cela de ta description, pas d’un tableau publié.",
            it = "Nomi lo ha ricavato dalla tua descrizione, non da una tabella pubblicata.",
            nl = "Nomi heeft dit afgeleid uit je omschrijving, niet uit een gepubliceerde tabel.",
            pt = "A Nomi deduziu isto da tua descrição, não de uma tabela publicada.",
            sq = "Nomi e nxori këtë nga përshkrimi yt, jo nga një tabelë e publikuar.",
            sv = "Nomi räknade ut det här från din beskrivning, inte från en publicerad tabell.",
            tr = "Nomi bunu yayımlanmış bir tablodan değil, senin açıklamandan çıkardı.",
        ),
    "No source page recorded these values." to NomiTranslation(
        de = "Keine Quellseite hat diese Werte festgehalten.",
        es = "Ninguna página de origen registró estos valores.",
        fr = "Aucune page source n’a enregistré ces valeurs.",
        it = "Nessuna pagina di origine ha registrato questi valori.",
        nl = "Geen bronpagina heeft deze waarden vastgelegd.",
        pt = "Nenhuma página de origem registou estes valores.",
        sq = "Asnjë faqe burimore nuk i ka regjistruar këto vlera.",
        sv = "Ingen källsida registrerade de här värdena.",
        tr = "Bu değerleri kaydeden bir kaynak sayfa yok.",
    ),
    "No sources. Nomi estimated this from your description." to NomiTranslation(
        de = "Keine Quellen. Nomi hat das aus deiner Beschreibung geschätzt.",
        es = "Sin fuentes. Nomi lo ha estimado a partir de tu descripción.",
        fr = "Aucune source. Nomi a estimé cela d’après ta description.",
        it = "Nessuna fonte. Nomi lo ha stimato dalla tua descrizione.",
        nl = "Geen bronnen. Nomi heeft dit geschat op basis van je omschrijving.",
        pt = "Sem fontes. A Nomi estimou isto a partir da tua descrição.",
        sq = "Pa burime. Nomi e vlerësoi këtë nga përshkrimi yt.",
        sv = "Inga källor. Nomi uppskattade det här utifrån din beskrivning.",
        tr = "Kaynak yok. Nomi bunu açıklamandan tahmin etti.",
    ),
    "No sources were recorded for this entry." to NomiTranslation(
        de = "Für diesen Eintrag wurden keine Quellen erfasst.",
        es = "No se registraron fuentes para esta entrada.",
        fr = "Aucune source n’a été enregistrée pour cette entrée.",
        it = "Per questa voce non è stata registrata alcuna fonte.",
        nl = "Voor dit item zijn geen bronnen vastgelegd.",
        pt = "Não foram registadas fontes para esta entrada.",
        sq = "Për këtë regjistrim nuk u regjistrua asnjë burim.",
        sv = "Inga källor registrerades för den här posten.",
        tr = "Bu kayıt için kaynak kaydedilmedi.",
    ),

    // References
    "References" to NomiTranslation(
        de = "Quellen", es = "Referencias", fr = "Références", it = "Riferimenti",
        nl = "Bronnen", pt = "Referências", sq = "Referencat", sv = "Källor",
        tr = "Kaynaklar",
    ),
    "{0} source" to NomiTranslation(
        de = "{0} Quelle", es = "{0} fuente", fr = "{0} source", it = "{0} fonte",
        nl = "{0} bron", pt = "{0} fonte", sq = "{0} burim", sv = "{0} källa",
        tr = "{0} kaynak",
    ),
    "{0} pages checked" to NomiTranslation(
        de = "{0} Seiten geprüft", es = "{0} páginas consultadas",
        fr = "{0} pages consultées", it = "{0} pagine consultate",
        nl = "{0} pagina’s bekeken", pt = "{0} páginas consultadas",
        sq = "{0} faqe të kontrolluara", sv = "{0} sidor kontrollerade",
        tr = "{0} sayfa incelendi",
    ),
    "{0} sources" to NomiTranslation(
        de = "{0} Quellen", es = "{0} fuentes", fr = "{0} sources", it = "{0} fonti",
        nl = "{0} bronnen", pt = "{0} fontes", sq = "{0} burime", sv = "{0} källor",
        tr = "{0} kaynak",
    ),
    "Show sources" to NomiTranslation(
        de = "Quellen anzeigen", es = "Mostrar las fuentes", fr = "Afficher les sources",
        it = "Mostra le fonti", nl = "Bronnen tonen", pt = "Mostrar as fontes",
        sq = "Shfaq burimet", sv = "Visa källor", tr = "Kaynakları göster",
    ),
    "Hide sources" to NomiTranslation(
        de = "Quellen ausblenden", es = "Ocultar las fuentes", fr = "Masquer les sources",
        it = "Nascondi le fonti", nl = "Bronnen verbergen", pt = "Ocultar as fontes",
        sq = "Fshih burimet", sv = "Dölj källor", tr = "Kaynakları gizle",
    ),
    "Open {0}" to NomiTranslation(
        de = "{0} öffnen", es = "Abrir {0}", fr = "Ouvrir {0}", it = "Apri {0}",
        nl = "{0} openen", pt = "Abrir {0}", sq = "Hap {0}", sv = "Öppna {0}",
        tr = "{0} adresini aç",
    ),
    "1 item • {0}" to NomiTranslation(
        de = "1 Eintrag • {0}", es = "1 elemento • {0}", fr = "1 élément • {0}",
        it = "1 voce • {0}", nl = "1 item • {0}", pt = "1 item • {0}",
        sq = "1 artikull • {0}", sv = "1 post • {0}", tr = "1 öğe • {0}",
    ),
    "Nomi estimated this entry for {0} using {1}. The serving size and matching food source " +
        "have the biggest effect on the result, so the actual nutrition may vary." to
        NomiTranslation(
            de = "Nomi hat diesen Eintrag für {0} anhand von {1} geschätzt. Portionsgröße und " +
                "passende Lebensmittelquelle beeinflussen das Ergebnis am stärksten, daher " +
                "können die tatsächlichen Nährwerte abweichen.",
            es = "Nomi estimó esta entrada para {0} a partir de {1}. El tamaño de la ración y la " +
                "fuente de alimento coincidente son lo que más influye en el resultado, así que " +
                "los valores reales pueden variar.",
            fr = "Nomi a estimé cette entrée pour {0} à partir de {1}. La taille de la portion " +
                "et la source alimentaire correspondante ont le plus d’effet sur le résultat, " +
                "les valeurs réelles peuvent donc varier.",
            it = "Nomi ha stimato questa voce per {0} usando {1}. La dimensione della porzione e " +
                "la fonte alimentare corrispondente incidono di più sul risultato, quindi i " +
                "valori reali possono variare.",
            nl = "Nomi heeft dit item geschat voor {0} op basis van {1}. De portiegrootte en de " +
                "gekozen voedingsbron hebben de meeste invloed op het resultaat, dus de echte " +
                "voedingswaarde kan afwijken.",
            pt = "A Nomi estimou esta entrada para {0} a partir de {1}. O tamanho da dose e a " +
                "fonte de alimento correspondente são o que mais influencia o resultado, por " +
                "isso os valores reais podem variar.",
            sq = "Nomi e vlerësoi këtë regjistrim për {0} duke përdorur {1}. Madhësia e porcionit " +
                "dhe burimi përkatës i ushqimit ndikojnë më së shumti te rezultati, prandaj " +
                "vlerat reale mund të ndryshojnë.",
            sv = "Nomi uppskattade posten för {0} utifrån {1}. Portionsstorleken och den " +
                "matchande livsmedelskällan påverkar resultatet mest, så de verkliga " +
                "näringsvärdena kan avvika.",
            tr = "Nomi bu kaydı {0} için {1} kullanarak tahmin etti. Sonucu en çok porsiyon " +
                "büyüklüğü ve eşleşen besin kaynağı etkiler, bu yüzden gerçek değerler farklı " +
                "olabilir.",
        ),
    "These totals use {0} and the logged amount of {1}. Change the serving amount if this does " +
        "not match what you ate." to NomiTranslation(
        de = "Diese Gesamtwerte basieren auf {0} und der eingetragenen Menge von {1}. Ändere die " +
            "Portionsmenge, wenn sie nicht dem entspricht, was du gegessen hast.",
        es = "Estos totales usan {0} y la cantidad registrada de {1}. Cambia la ración si no " +
            "coincide con lo que comiste.",
        fr = "Ces totaux utilisent {0} et la quantité enregistrée de {1}. Modifie la portion si " +
            "elle ne correspond pas à ce que tu as mangé.",
        it = "Questi totali usano {0} e la quantità registrata di {1}. Cambia la porzione se non " +
            "corrisponde a quello che hai mangiato.",
        nl = "Deze totalen gebruiken {0} en de vastgelegde hoeveelheid van {1}. Pas de portie " +
            "aan als dit niet klopt met wat je at.",
        pt = "Estes totais usam {0} e a quantidade registada de {1}. Altera a dose se não " +
            "corresponder ao que comeste.",
        sq = "Këto totale përdorin {0} dhe sasinë e regjistruar prej {1}. Ndrysho porcionin nëse " +
            "nuk përputhet me atë që hëngre.",
        sv = "Summorna bygger på {0} och den loggade mängden {1}. Ändra portionen om den inte " +
            "stämmer med vad du åt.",
        tr = "Bu toplamlar {0} ve kaydedilen {1} miktarını kullanır. Yediğinle uyuşmuyorsa " +
            "porsiyon miktarını değiştir.",
    ),

    // Food logging flow
    "Add food" to NomiTranslation(
        de = "Essen hinzufügen", es = "Añadir comida", fr = "Ajouter un aliment",
        it = "Aggiungi cibo", nl = "Eten toevoegen", pt = "Adicionar comida",
        sq = "Shto ushqim", sv = "Lägg till mat", tr = "Yemek ekle",
    ),
    "What did you eat?" to NomiTranslation(
        de = "Was hast du gegessen?", es = "¿Qué has comido?", fr = "Qu’as-tu mangé ?",
        it = "Che cosa hai mangiato?", nl = "Wat heb je gegeten?", pt = "O que comeste?",
        sq = "Çfarë hëngre?", sv = "Vad har du ätit?", tr = "Ne yedin?",
    ),
    "Try “two slices of toast with butter and a banana” or “250 g Skyr and a banana”." to
        NomiTranslation(
            de = "Versuche zum Beispiel „zwei Scheiben Toast mit Butter und einer Banane“ oder " +
                "„250 g Skyr und eine Banane“.",
            es = "Prueba con «dos rebanadas de pan tostado con mantequilla y un plátano» o «250 g " +
                "de skyr y un plátano».",
            fr = "Essaie « deux tranches de pain grillé avec du beurre et une banane » ou « 250 g " +
                "de skyr et une banane ».",
            it = "Prova con «due fette di pane tostato con burro e una banana» o «250 g di skyr e " +
                "una banana».",
            nl = "Probeer “twee sneetjes toast met boter en een banaan” of “250 g skyr en een " +
                "banaan”.",
            pt = "Experimenta «duas fatias de tosta com manteiga e uma banana» ou «250 g de skyr " +
                "e uma banana».",
            sq = "Provo “dy feta bukë e thekur me gjalpë dhe një banane” ose “250 g skyr dhe një " +
                "banane”.",
            sv = "Testa ”två skivor rostat bröd med smör och en banan” eller ”250 g skyr och en " +
                "banan”.",
            tr = "“Tereyağlı iki dilim tost ve bir muz” ya da “250 g skyr ve bir muz” diye dene.",
        ),
    "Tell Nomi what you ate" to NomiTranslation(
        de = "Sag Nomi, was du gegessen hast", es = "Cuéntale a Nomi qué has comido",
        fr = "Dis à Nomi ce que tu as mangé", it = "Dì a Nomi che cosa hai mangiato",
        nl = "Vertel Nomi wat je hebt gegeten", pt = "Diz à Nomi o que comeste",
        sq = "Trego Nomit çfarë hëngre", sv = "Berätta för Nomi vad du åt",
        tr = "Nomi’ye ne yediğini söyle",
    ),
    "Your own language and English both work naturally." to NomiTranslation(
        de = "Deine eigene Sprache und Englisch funktionieren gleichermaßen natürlich.",
        es = "Tu propio idioma y el inglés funcionan con la misma naturalidad.",
        fr = "Ta propre langue et l’anglais fonctionnent aussi naturellement l’une que l’autre.",
        it = "La tua lingua e l’inglese funzionano entrambe in modo naturale.",
        nl = "Je eigen taal en Engels werken allebei even natuurlijk.",
        pt = "A tua própria língua e o inglês funcionam com a mesma naturalidade.",
        sq = "Gjuha jote dhe anglishtja funksionojnë të dyja natyrshëm.",
        sv = "Ditt eget språk och engelska fungerar lika naturligt.",
        tr = "Kendi dilin de İngilizce de aynı doğallıkla çalışır.",
    ),
    "Understand meal" to NomiTranslation(
        de = "Mahlzeit verstehen", es = "Entender la comida", fr = "Comprendre le repas",
        it = "Interpreta il pasto", nl = "Maaltijd begrijpen", pt = "Perceber a refeição",
        sq = "Kupto vaktin", sv = "Tolka måltiden", tr = "Öğünü anla",
    ),
    "Is this what you ate?" to NomiTranslation(
        de = "Ist das, was du gegessen hast?", es = "¿Es esto lo que comiste?",
        fr = "Est-ce bien ce que tu as mangé ?", it = "È questo che hai mangiato?",
        nl = "Is dit wat je hebt gegeten?", pt = "Foi isto que comeste?",
        sq = "A është kjo ajo që hëngre?", sv = "Är det här vad du åt?",
        tr = "Yediğin bu mu?",
    ),
    "Nomi read your photo as the words below. Fix anything it got wrong before it looks up the " +
        "nutrition." to NomiTranslation(
        de = "Nomi hat dein Foto als den folgenden Text gelesen. Korrigiere alles Falsche, bevor " +
            "die Nährwerte recherchiert werden.",
        es = "Nomi ha leído tu foto como el texto de abajo. Corrige lo que esté mal antes de que " +
            "busque la información nutricional.",
        fr = "Nomi a lu ta photo comme le texte ci-dessous. Corrige ce qui est faux avant la " +
            "recherche nutritionnelle.",
        it = "Nomi ha letto la tua foto come il testo qui sotto. Correggi gli errori prima che " +
            "cerchi i valori nutrizionali.",
        nl = "Nomi heeft je foto gelezen als de tekst hieronder. Corrigeer fouten voordat de " +
            "voedingswaarde wordt opgezocht.",
        pt = "A Nomi leu a tua foto como o texto abaixo. Corrige o que estiver errado antes de " +
            "procurar os valores nutricionais.",
        sq = "Nomi e lexoi foton tënde si tekstin më poshtë. Korrigjo çdo gabim para se të " +
            "kërkojë vlerat ushqyese.",
        sv = "Nomi läste ditt foto som texten nedan. Rätta det som blivit fel innan " +
            "näringsvärdena slås upp.",
        tr = "Nomi fotoğrafını aşağıdaki metin olarak okudu. Besin değerlerini aramadan önce " +
            "yanlışları düzelt.",
    ),
    "What's in the photo" to NomiTranslation(
        de = "Was auf dem Foto ist", es = "Qué hay en la foto", fr = "Ce qu’il y a sur la photo",
        it = "Che cosa c’è nella foto", nl = "Wat er op de foto staat", pt = "O que está na foto",
        sq = "Çfarë ka në foto", sv = "Vad som finns på fotot", tr = "Fotoğraftakiler",
    ),
    "Correct a food, an amount, or an ingredient — it reads like anything you'd type." to
        NomiTranslation(
            de = "Korrigiere ein Lebensmittel, eine Menge oder eine Zutat – es liest sich wie " +
                "alles, was du tippst.",
            es = "Corrige un alimento, una cantidad o un ingrediente: se lee igual que cualquier " +
                "cosa que escribas.",
            fr = "Corrige un aliment, une quantité ou un ingrédient — cela se lit comme tout ce " +
                "que tu écrirais.",
            it = "Correggi un alimento, una quantità o un ingrediente: si legge come qualsiasi " +
                "cosa tu scriva.",
            nl = "Corrigeer een product, een hoeveelheid of een ingrediënt — het leest als alles " +
                "wat je zelf typt.",
            pt = "Corrige um alimento, uma quantidade ou um ingrediente — lê-se como qualquer " +
                "coisa que escrevas.",
            sq = "Korrigjo një ushqim, një sasi ose një përbërës – lexohet si çdo gjë që shkruan.",
            sv = "Rätta ett livsmedel, en mängd eller en ingrediens – det läses som allt annat " +
                "du skriver.",
            tr = "Bir besini, miktarı ya da malzemeyi düzelt — yazdığın her şey gibi okunur.",
        ),
    "Restaurant or shop (optional)" to NomiTranslation(
        de = "Restaurant oder Laden (optional)", es = "Restaurante o tienda (opcional)",
        fr = "Restaurant ou magasin (facultatif)", it = "Ristorante o negozio (facoltativo)",
        nl = "Restaurant of winkel (optioneel)", pt = "Restaurante ou loja (opcional)",
        sq = "Restorant ose dyqan (opsional)", sv = "Restaurang eller butik (valfritt)",
        tr = "Restoran veya mağaza (isteğe bağlı)",
    ),
    "e.g. Five Guys" to NomiTranslation(
        de = "z. B. Five Guys", es = "p. ej. Five Guys", fr = "par ex. Five Guys",
        it = "ad es. Five Guys", nl = "bijv. Five Guys", pt = "p. ex. Five Guys",
        sq = "p.sh. Five Guys", sv = "t.ex. Five Guys", tr = "örn. Five Guys",
    ),
    "Naming the place sends Nomi to its own published nutrition instead of a generic recipe." to
        NomiTranslation(
            de = "Mit dem Namen sucht Nomi in den offiziellen Nährwerten des Anbieters statt in " +
                "einem allgemeinen Rezept.",
            es = "Si indicas el sitio, Nomi consulta sus valores nutricionales publicados en vez " +
                "de una receta genérica.",
            fr = "En nommant l’enseigne, Nomi consulte ses valeurs nutritionnelles publiées " +
                "plutôt qu’une recette générique.",
            it = "Indicando il locale, Nomi consulta i valori nutrizionali pubblicati invece di " +
                "una ricetta generica.",
            nl = "Als je de zaak noemt, gebruikt Nomi de gepubliceerde voedingswaarden in plaats " +
                "van een algemeen recept.",
            pt = "Ao indicares o local, a Nomi consulta os valores nutricionais publicados em vez " +
                "de uma receita genérica.",
            sq = "Duke emërtuar vendin, Nomi shkon te vlerat zyrtare të publikuara prej tij në " +
                "vend të një recete të përgjithshme.",
            sv = "Om du anger stället använder Nomi deras publicerade näringsvärden i stället " +
                "för ett generiskt recept.",
            tr = "Mekânın adını yazarsan Nomi genel bir tarif yerine onun yayımladığı besin " +
                "değerlerine bakar.",
        ),
    "Find nutrition" to NomiTranslation(
        de = "Nährwerte suchen", es = "Buscar la nutrición", fr = "Rechercher la nutrition",
        it = "Cerca i valori nutrizionali", nl = "Voedingswaarde zoeken",
        pt = "Procurar os valores nutricionais", sq = "Gjej vlerat ushqyese",
        sv = "Sök näringsvärden", tr = "Besin değerlerini bul",
    ),
    "Nomi checks sources against the exact food and amount before showing calories." to
        NomiTranslation(
            de = "Nomi prüft Quellen, Lebensmittel und Menge, bevor Kalorien angezeigt werden.",
            es = "Nomi contrasta las fuentes con el alimento y la cantidad exactos antes de " +
                "mostrar calorías.",
            fr = "Nomi vérifie les sources par rapport à l’aliment et à la quantité exacts avant " +
                "d’afficher des calories.",
            it = "Nomi verifica le fonti rispetto all’alimento e alla quantità esatti prima di " +
                "mostrare le calorie.",
            nl = "Nomi toetst bronnen aan het exacte product en de exacte hoeveelheid voordat " +
                "calorieën worden getoond.",
            pt = "A Nomi confronta as fontes com o alimento e a quantidade exatos antes de " +
                "mostrar calorias.",
            sq = "Nomi i verifikon burimet me ushqimin dhe sasinë e saktë para se të shfaqë kalori.",
            sv = "Nomi stämmer av källorna mot exakt livsmedel och mängd innan kalorier visas.",
            tr = "Nomi kalorileri göstermeden önce kaynakları tam besin ve miktarla karşılaştırır.",
        ),
    "Nomi is turning your description into editable foods." to NomiTranslation(
        de = "Nomi verwandelt deine Beschreibung in bearbeitbare Lebensmittel.",
        es = "Nomi está convirtiendo tu descripción en alimentos editables.",
        fr = "Nomi transforme ta description en aliments modifiables.",
        it = "Nomi sta trasformando la tua descrizione in alimenti modificabili.",
        nl = "Nomi zet je omschrijving om in bewerkbare producten.",
        pt = "A Nomi está a transformar a tua descrição em alimentos editáveis.",
        sq = "Nomi po e kthen përshkrimin tënd në ushqime të redaktueshme.",
        sv = "Nomi omvandlar din beskrivning till redigerbara livsmedel.",
        tr = "Nomi açıklamanı düzenlenebilir besinlere dönüştürüyor.",
    ),
    "Detected" to NomiTranslation(
        de = "Erkannt", es = "Detectado", fr = "Détecté", it = "Rilevato", nl = "Herkend",
        pt = "Detetado", sq = "U dallua", sv = "Identifierat", tr = "Algılandı",
    ),
    "Check the portions before saving." to NomiTranslation(
        de = "Prüfe die Portionen vor dem Speichern.",
        es = "Revisa las porciones antes de guardar.",
        fr = "Vérifie les portions avant d’enregistrer.",
        it = "Controlla le porzioni prima di salvare.",
        nl = "Controleer de porties voordat je opslaat.",
        pt = "Verifica as porções antes de guardar.",
        sq = "Kontrollo porcionet para se të ruash.",
        sv = "Kontrollera portionerna innan du sparar.",
        tr = "Kaydetmeden önce porsiyonları kontrol et.",
    ),
    "Total" to NomiTranslation(
        de = "Gesamt", es = "Total", fr = "Total", it = "Totale", nl = "Totaal",
        pt = "Total", sq = "Totali", sv = "Totalt", tr = "Toplam",
    ),
    "Add to today" to NomiTranslation(
        de = "Zu Heute hinzufügen", es = "Añadir a hoy", fr = "Ajouter à aujourd’hui",
        it = "Aggiungi a oggi", nl = "Aan vandaag toevoegen", pt = "Adicionar a hoje",
        sq = "Shto te sot", sv = "Lägg till i idag", tr = "Bugüne ekle",
    ),
    "AI and portion values can be estimates. You can edit every item." to NomiTranslation(
        de = "KI- und Portionswerte können Schätzungen sein. Du kannst jeden Eintrag bearbeiten.",
        es = "Los valores de la IA y de las porciones pueden ser estimaciones. Puedes editar " +
            "cada elemento.",
        fr = "Les valeurs de l’IA et des portions peuvent être des estimations. Tu peux modifier " +
            "chaque élément.",
        it = "I valori dell’IA e delle porzioni possono essere stime. Puoi modificare ogni voce.",
        nl = "AI- en portiewaarden kunnen schattingen zijn. Je kunt elk item bewerken.",
        pt = "Os valores da IA e das porções podem ser estimativas. Podes editar cada item.",
        sq = "Vlerat e IA-së dhe të porcioneve mund të jenë vlerësime. Mund të redaktosh çdo artikull.",
        sv = "AI- och portionsvärden kan vara uppskattningar. Du kan redigera varje post.",
        tr = "YZ ve porsiyon değerleri tahmini olabilir. Her öğeyi düzenleyebilirsin.",
    ),
    "Try again" to NomiTranslation(
        de = "Erneut versuchen", es = "Inténtalo de nuevo", fr = "Réessayer", it = "Riprova",
        nl = "Opnieuw proberen", pt = "Tentar novamente", sq = "Provo sërish",
        sv = "Försök igen", tr = "Tekrar dene",
    ),
    "Enter food manually" to NomiTranslation(
        de = "Lebensmittel manuell eingeben", es = "Introducir el alimento manualmente",
        fr = "Saisir l’aliment manuellement", it = "Inserisci l’alimento manualmente",
        nl = "Product handmatig invoeren", pt = "Introduzir o alimento manualmente",
        sq = "Fut ushqimin manualisht", sv = "Ange livsmedel manuellt",
        tr = "Besini elle gir",
    ),
    "Name" to NomiTranslation(
        de = "Name", es = "Nombre", fr = "Nom", it = "Nome", nl = "Naam", pt = "Nome",
        sq = "Emri", sv = "Namn", tr = "Ad",
    ),
    "Amount" to NomiTranslation(
        de = "Menge", es = "Cantidad", fr = "Quantité", it = "Quantità", nl = "Hoeveelheid",
        pt = "Quantidade", sq = "Sasia", sv = "Mängd", tr = "Miktar",
    ),
    "Protein (g)" to NomiTranslation(
        de = "Eiweiß (g)", es = "Proteínas (g)", fr = "Protéines (g)", it = "Proteine (g)",
        nl = "Eiwitten (g)", pt = "Proteínas (g)", sq = "Proteina (g)", sv = "Protein (g)",
        tr = "Protein (g)",
    ),
    "Carbohydrates (g)" to NomiTranslation(
        de = "Kohlenhydrate (g)", es = "Carbohidratos (g)", fr = "Glucides (g)",
        it = "Carboidrati (g)", nl = "Koolhydraten (g)", pt = "Hidratos de carbono (g)",
        sq = "Karbohidrate (g)", sv = "Kolhydrater (g)", tr = "Karbonhidrat (g)",
    ),
    "Fat (g)" to NomiTranslation(
        de = "Fett (g)", es = "Grasas (g)", fr = "Lipides (g)", it = "Grassi (g)",
        nl = "Vetten (g)", pt = "Gorduras (g)", sq = "Yndyrna (g)", sv = "Fett (g)",
        tr = "Yağ (g)",
    ),
    "Meal" to NomiTranslation(
        de = "Mahlzeit", es = "Comida", fr = "Repas", it = "Pasto", nl = "Maaltijd",
        pt = "Refeição", sq = "Vakt", sv = "Måltid", tr = "Öğün",
    ),
    // The logged unit for a combined order. German capitalizes it because it is a noun there.
    "meal" to NomiTranslation(
        de = "Menü", es = "comida", fr = "repas", it = "pasto", nl = "maaltijd",
        pt = "refeição", sq = "vakt", sv = "måltid", tr = "öğün",
    ),

    // Written into saved data or shown as an error from outside a composition.
    "Photographed label" to NomiTranslation(
        de = "Fotografiertes Etikett", es = "Etiqueta fotografiada",
        fr = "Étiquette photographiée", it = "Etichetta fotografata",
        nl = "Gefotografeerd etiket", pt = "Rótulo fotografado",
        sq = "Etiketë e fotografuar", sv = "Fotograferad etikett",
        tr = "Fotoğraflanan etiket",
    ),
    "Nutrition label photo" to NomiTranslation(
        de = "Foto der Nährwerttabelle", es = "Foto de la etiqueta nutricional",
        fr = "Photo de l’étiquette nutritionnelle", it = "Foto della tabella nutrizionale",
        nl = "Foto van de voedingswaardetabel", pt = "Foto do rótulo nutricional",
        sq = "Foto e etiketës ushqyese", sv = "Foto av näringsdeklarationen",
        tr = "Besin etiketi fotoğrafı",
    ),
    "Nomi couldn't read that menu page. Add a clearer photo." to NomiTranslation(
        de = "Nomi konnte diese Speisekartenseite nicht lesen. Füge ein deutlicheres Foto hinzu.",
        es = "Nomi no ha podido leer esa página de la carta. Añade una foto más nítida.",
        fr = "Nomi n’a pas pu lire cette page du menu. Ajoute une photo plus nette.",
        it = "Nomi non è riuscito a leggere questa pagina del menù. Aggiungi una foto più nitida.",
        nl = "Nomi kon die menupagina niet lezen. Voeg een scherpere foto toe.",
        pt = "A Nomi não conseguiu ler essa página da ementa. Adiciona uma foto mais nítida.",
        sq = "Nomi nuk mundi ta lexojë atë faqe të menusë. Shto një foto më të qartë.",
        sv = "Nomi kunde inte läsa den menysidan. Lägg till ett tydligare foto.",
        tr = "Nomi bu menü sayfasını okuyamadı. Daha net bir fotoğraf ekle.",
    ),
    "Nomi couldn't find nutrition for that change. Try again." to NomiTranslation(
        de = "Nomi hat für diese Änderung keine Nährwerte gefunden. Versuch es erneut.",
        es = "Nomi no ha encontrado información nutricional para ese cambio. Inténtalo de nuevo.",
        fr = "Nomi n’a pas trouvé de valeurs nutritionnelles pour ce changement. Réessaie.",
        it = "Nomi non ha trovato valori nutrizionali per questa modifica. Riprova.",
        nl = "Nomi vond geen voedingswaarde voor die wijziging. Probeer opnieuw.",
        pt = "A Nomi não encontrou valores nutricionais para essa alteração. Tenta de novo.",
        sq = "Nomi nuk gjeti vlera ushqyese për atë ndryshim. Provo sërish.",
        sv = "Nomi hittade inga näringsvärden för den ändringen. Försök igen.",
        tr = "Nomi bu değişiklik için besin değeri bulamadı. Tekrar dene.",
    ),
    "Nomi couldn't verify nutrition for every product. Try again or edit the entry." to
        NomiTranslation(
            de = "Nomi konnte nicht für alle Produkte passende Nährwerte belegen. Versuche es " +
                "erneut oder bearbeite die Eingabe.",
            es = "Nomi no ha podido verificar la nutrición de todos los productos. Inténtalo de " +
                "nuevo o edita la entrada.",
            fr = "Nomi n’a pas pu vérifier les valeurs nutritionnelles de tous les produits. " +
                "Réessaie ou modifie l’entrée.",
            it = "Nomi non è riuscito a verificare i valori nutrizionali di tutti i prodotti. " +
                "Riprova oppure modifica la voce.",
            nl = "Nomi kon niet voor elk product de voedingswaarde verifiëren. Probeer opnieuw " +
                "of bewerk het item.",
            pt = "A Nomi não conseguiu verificar os valores nutricionais de todos os produtos. " +
                "Tenta de novo ou edita a entrada.",
            sq = "Nomi nuk mundi t’i verifikojë vlerat ushqyese për çdo produkt. Provo sërish " +
                "ose redakto regjistrimin.",
            sv = "Nomi kunde inte verifiera näringsvärden för alla produkter. Försök igen eller " +
                "redigera posten.",
            tr = "Nomi her ürün için besin değerlerini doğrulayamadı. Tekrar dene ya da kaydı " +
                "düzenle.",
        ),
    "Understanding your meal" to NomiTranslation(
        de = "Deine Mahlzeit wird verstanden", es = "Entendiendo tu comida",
        fr = "Compréhension de ton repas", it = "Sto interpretando il tuo pasto",
        nl = "Je maaltijd wordt begrepen", pt = "A perceber a tua refeição",
        sq = "Po kuptohet vakti yt", sv = "Tolkar din måltid", tr = "Öğünün anlaşılıyor",
    ),
    "Finding nutrition information" to NomiTranslation(
        de = "Nährwertinformationen werden gesucht", es = "Buscando información nutricional",
        fr = "Recherche des informations nutritionnelles",
        it = "Ricerca delle informazioni nutrizionali", nl = "Voedingsinformatie zoeken",
        pt = "A procurar informação nutricional", sq = "Po kërkohen të dhënat ushqyese",
        sv = "Söker näringsinformation", tr = "Besin bilgileri aranıyor",
    ),
    "Checking portions" to NomiTranslation(
        de = "Portionen werden geprüft", es = "Comprobando las porciones",
        fr = "Vérification des portions", it = "Controllo delle porzioni",
        nl = "Porties controleren", pt = "A verificar as porções",
        sq = "Po kontrollohen porcionet", sv = "Kontrollerar portioner",
        tr = "Porsiyonlar kontrol ediliyor",
    ),
    "Putting it together" to NomiTranslation(
        de = "Alles wird zusammengestellt", es = "Juntándolo todo", fr = "Assemblage en cours",
        it = "Sto mettendo insieme il tutto", nl = "Alles wordt samengevoegd",
        pt = "A juntar tudo", sq = "Po bashkohet gjithçka", sv = "Sätter ihop allt",
        tr = "Her şey birleştiriliyor",
    ),
    "Source: {0}" to NomiTranslation(
        de = "Quelle: {0}", es = "Fuente: {0}", fr = "Source : {0}", it = "Fonte: {0}",
        nl = "Bron: {0}", pt = "Fonte: {0}", sq = "Burimi: {0}", sv = "Källa: {0}",
        tr = "Kaynak: {0}",
    ),
    "Change {0} portion with AI" to NomiTranslation(
        de = "Portion von {0} mit KI ändern", es = "Cambiar la porción de {0} con IA",
        fr = "Modifier la portion de {0} avec l’IA", it = "Cambia la porzione di {0} con l’IA",
        nl = "Portie van {0} aanpassen met AI", pt = "Alterar a porção de {0} com IA",
        sq = "Ndrysho porcionin e {0} me IA", sv = "Ändra portionen för {0} med AI",
        tr = "{0} porsiyonunu YZ ile değiştir",
    ),
    "Edit {0} manually" to NomiTranslation(
        de = "{0} manuell bearbeiten", es = "Editar {0} manualmente",
        fr = "Modifier {0} manuellement", it = "Modifica {0} manualmente",
        nl = "{0} handmatig bewerken", pt = "Editar {0} manualmente",
        sq = "Redakto {0} manualisht", sv = "Redigera {0} manuellt",
        tr = "{0} öğesini elle düzenle",
    ),
    "Remove {0} from meal" to NomiTranslation(
        de = "{0} aus dem Menü entfernen", es = "Quitar {0} de la comida",
        fr = "Retirer {0} du repas", it = "Rimuovi {0} dal pasto",
        nl = "{0} uit de maaltijd verwijderen", pt = "Remover {0} da refeição",
        sq = "Hiq {0} nga vakti", sv = "Ta bort {0} från måltiden",
        tr = "{0} öğesini öğünden çıkar",
    ),

    // Portion editor
    "Change this food" to NomiTranslation(
        de = "Dieses Essen ändern", es = "Cambiar este alimento", fr = "Modifier cet aliment",
        it = "Cambia questo alimento", nl = "Dit product wijzigen", pt = "Alterar este alimento",
        sq = "Ndrysho këtë ushqim", sv = "Ändra det här livsmedlet", tr = "Bu besini değiştir",
    ),
    "What should I change?" to NomiTranslation(
        de = "Was soll ich ändern?", es = "¿Qué cambio?", fr = "Que dois-je changer ?",
        it = "Che cosa devo cambiare?", nl = "Wat moet ik veranderen?", pt = "O que devo alterar?",
        sq = "Çfarë duhet të ndryshoj?", sv = "Vad ska jag ändra?", tr = "Neyi değiştireyim?",
    ),
    "Half, or actually it was tuna" to NomiTranslation(
        de = "Die Hälfte, oder es war doch Thunfisch",
        es = "La mitad, o en realidad era atún",
        fr = "La moitié, ou en fait c’était du thon",
        it = "La metà, anzi era tonno",
        nl = "De helft, of eigenlijk was het tonijn",
        pt = "Metade, ou na verdade era atum",
        sq = "Gjysma, ose në fakt ishte ton",
        sv = "Hälften, eller det var faktiskt tonfisk",
        tr = "Yarısı, ya da aslında ton balığıydı",
    ),
    "Interpreting…" to NomiTranslation(
        de = "Wird interpretiert…", es = "Interpretando…", fr = "Interprétation…",
        it = "Interpretazione…", nl = "Interpreteren…", pt = "A interpretar…",
        sq = "Po interpretohet…", sv = "Tolkar…", tr = "Yorumlanıyor…",
    ),
    "Preview change" to NomiTranslation(
        de = "Änderung prüfen", es = "Ver el cambio", fr = "Prévisualiser la modification",
        it = "Anteprima della modifica", nl = "Wijziging bekijken", pt = "Pré-ver a alteração",
        sq = "Parashiko ndryshimin", sv = "Förhandsgranska ändringen",
        tr = "Değişikliği önizle",
    ),
    "That changes the food, not just the amount" to NomiTranslation(
        de = "Das ändert das Essen, nicht nur die Menge",
        es = "Eso cambia el alimento, no solo la cantidad",
        fr = "Cela change l’aliment, pas seulement la quantité",
        it = "Così cambia l’alimento, non solo la quantità",
        nl = "Dat verandert het product, niet alleen de hoeveelheid",
        pt = "Isso altera o alimento, não apenas a quantidade",
        sq = "Kjo ndryshon ushqimin, jo vetëm sasinë",
        sv = "Det ändrar livsmedlet, inte bara mängden",
        tr = "Bu, yalnızca miktarı değil besini değiştirir",
    ),
    "Nomi needs to look up nutrition for the corrected food." to NomiTranslation(
        de = "Nomi muss die Nährwerte für das korrigierte Essen neu nachschlagen.",
        es = "Nomi necesita buscar la nutrición del alimento corregido.",
        fr = "Nomi doit rechercher les valeurs nutritionnelles de l’aliment corrigé.",
        it = "Nomi deve cercare i valori nutrizionali dell’alimento corretto.",
        nl = "Nomi moet de voedingswaarde van het gecorrigeerde product opzoeken.",
        pt = "A Nomi precisa de procurar os valores nutricionais do alimento corrigido.",
        sq = "Nomi duhet të kërkojë vlerat ushqyese për ushqimin e korrigjuar.",
        sv = "Nomi behöver slå upp näringsvärden för det rättade livsmedlet.",
        tr = "Nomi’nin düzeltilen besin için besin değerlerini araması gerekiyor.",
    ),
    "Looking it up…" to NomiTranslation(
        de = "Wird nachgeschlagen…", es = "Buscándolo…", fr = "Recherche en cours…",
        it = "Ricerca in corso…", nl = "Wordt opgezocht…", pt = "A procurar…",
        sq = "Po kërkohet…", sv = "Slår upp…", tr = "Aranıyor…",
    ),
    "Look it up again" to NomiTranslation(
        de = "Neu nachschlagen", es = "Buscarlo otra vez", fr = "Rechercher à nouveau",
        it = "Cerca di nuovo", nl = "Opnieuw opzoeken", pt = "Procurar de novo",
        sq = "Kërko sërish", sv = "Slå upp igen", tr = "Yeniden ara",
    ),
    "Before" to NomiTranslation(
        de = "Vorher", es = "Antes", fr = "Avant", it = "Prima", nl = "Voor", pt = "Antes",
        sq = "Para", sv = "Före", tr = "Önce",
    ),
    "After" to NomiTranslation(
        de = "Nachher", es = "Después", fr = "Après", it = "Dopo", nl = "Na", pt = "Depois",
        sq = "Pas", sv = "Efter", tr = "Sonra",
    ),
    "Apply" to NomiTranslation(
        de = "Übernehmen", es = "Aplicar", fr = "Appliquer", it = "Applica", nl = "Toepassen",
        pt = "Aplicar", sq = "Zbato", sv = "Tillämpa", tr = "Uygula",
    ),
)
