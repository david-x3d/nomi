package com.nomi.app.ui.localization

/**
 * Navigation, shared actions, the weight sheet, Health Connect and the debug screen.
 *
 * Nomi speaks to the user informally in every language, so these follow the German "du" the app
 * already used: "tu" in French, Italian, Spanish and Portuguese, "je" in Dutch, "du" in Swedish,
 * and the singular forms in Turkish and Albanian.
 */
internal val commonTranslations: Map<String, NomiTranslation> = mapOf(
    "Today" to NomiTranslation(
        de = "Heute", es = "Hoy", fr = "Aujourd’hui", it = "Oggi", nl = "Vandaag",
        pt = "Hoje", sq = "Sot", sv = "Idag", tr = "Bugün",
    ),
    "Progress" to NomiTranslation(
        de = "Fortschritt", es = "Progreso", fr = "Progression", it = "Progressi", nl = "Voortgang",
        pt = "Progresso", sq = "Përparimi", sv = "Utveckling", tr = "İlerleme",
    ),
    "Settings" to NomiTranslation(
        de = "Einstellungen", es = "Ajustes", fr = "Réglages", it = "Impostazioni",
        nl = "Instellingen", pt = "Definições", sq = "Cilësimet", sv = "Inställningar",
        tr = "Ayarlar",
    ),
    "Save" to NomiTranslation(
        de = "Speichern", es = "Guardar", fr = "Enregistrer", it = "Salva", nl = "Opslaan",
        pt = "Guardar", sq = "Ruaj", sv = "Spara", tr = "Kaydet",
    ),
    "Cancel" to NomiTranslation(
        de = "Abbrechen", es = "Cancelar", fr = "Annuler", it = "Annulla", nl = "Annuleren",
        pt = "Cancelar", sq = "Anulo", sv = "Avbryt", tr = "İptal",
    ),
    "Delete" to NomiTranslation(
        de = "Löschen", es = "Eliminar", fr = "Supprimer", it = "Elimina", nl = "Verwijderen",
        pt = "Eliminar", sq = "Fshi", sv = "Radera", tr = "Sil",
    ),
    "Duplicate" to NomiTranslation(
        de = "Duplizieren", es = "Duplicar", fr = "Dupliquer", it = "Duplica", nl = "Dupliceren",
        pt = "Duplicar", sq = "Dyfisho", sv = "Duplicera", tr = "Çoğalt",
    ),
    "Favorite" to NomiTranslation(
        de = "Favorit", es = "Favorito", fr = "Favori", it = "Preferito", nl = "Favoriet",
        pt = "Favorito", sq = "I preferuar", sv = "Favorit", tr = "Favori",
    ),
    "Back" to NomiTranslation(
        de = "Zurück", es = "Atrás", fr = "Retour", it = "Indietro", nl = "Terug",
        pt = "Voltar", sq = "Prapa", sv = "Tillbaka", tr = "Geri",
    ),
    "Go back" to NomiTranslation(
        de = "Zurück", es = "Volver", fr = "Revenir", it = "Torna indietro", nl = "Terug",
        pt = "Voltar", sq = "Kthehu", sv = "Gå tillbaka", tr = "Geri dön",
    ),
    "Choose an option" to NomiTranslation(
        de = "Option auswählen", es = "Elige una opción", fr = "Choisis une option",
        it = "Scegli un’opzione", nl = "Kies een optie", pt = "Escolhe uma opção",
        sq = "Zgjidh një opsion", sv = "Välj ett alternativ", tr = "Bir seçenek seç",
    ),
    "Nomi fox logo" to NomiTranslation(
        de = "Nomi-Fuchslogo", es = "Logotipo del zorro de Nomi", fr = "Logo renard de Nomi",
        it = "Logo volpe di Nomi", nl = "Nomi-vossenlogo", pt = "Logótipo raposa da Nomi",
        sq = "Logoja e dhelprës së Nomit", sv = "Nomis rävlogotyp", tr = "Nomi tilki logosu",
    ),

    // Weight sheet
    "Log weight" to NomiTranslation(
        de = "Gewicht eintragen", es = "Registrar peso", fr = "Enregistrer le poids",
        it = "Registra il peso", nl = "Gewicht vastleggen", pt = "Registar o peso",
        sq = "Regjistro peshën", sv = "Registrera vikt", tr = "Kilo kaydet",
    ),
    "Your trend matters more than any single weigh-in." to NomiTranslation(
        de = "Dein Verlauf ist wichtiger als eine einzelne Messung.",
        es = "Tu tendencia importa más que cualquier pesaje aislado.",
        fr = "Ta tendance compte plus qu’une pesée isolée.",
        it = "L’andamento conta più di una singola pesata.",
        nl = "Je trend zegt meer dan één weegmoment.",
        pt = "A tua tendência importa mais do que uma pesagem isolada.",
        sq = "Tendenca jote ka më shumë rëndësi se një matje e vetme.",
        sv = "Din trend säger mer än en enskild vägning.",
        tr = "Eğilimin, tek bir tartıdan daha önemlidir.",
    ),
    "Weight in kg" to NomiTranslation(
        de = "Gewicht in kg", es = "Peso en kg", fr = "Poids en kg", it = "Peso in kg",
        nl = "Gewicht in kg", pt = "Peso em kg", sq = "Pesha në kg", sv = "Vikt i kg",
        tr = "Kilo (kg)",
    ),
    "Note (optional)" to NomiTranslation(
        de = "Notiz (optional)", es = "Nota (opcional)", fr = "Note (facultatif)",
        it = "Nota (facoltativa)", nl = "Notitie (optioneel)", pt = "Nota (opcional)",
        sq = "Shënim (opsional)", sv = "Anteckning (valfritt)", tr = "Not (isteğe bağlı)",
    ),

    // Food details fallback screen
    "Food details" to NomiTranslation(
        de = "Lebensmitteldetails", es = "Detalles del alimento", fr = "Détails de l’aliment",
        it = "Dettagli dell’alimento", nl = "Voedingsdetails", pt = "Detalhes do alimento",
        sq = "Detajet e ushqimit", sv = "Livsmedelsdetaljer", tr = "Besin ayrıntıları",
    ),
    "This entry is no longer available." to NomiTranslation(
        de = "Dieser Eintrag ist nicht mehr verfügbar.",
        es = "Esta entrada ya no está disponible.",
        fr = "Cette entrée n’est plus disponible.",
        it = "Questa voce non è più disponibile.",
        nl = "Dit item is niet meer beschikbaar.",
        pt = "Esta entrada já não está disponível.",
        sq = "Ky regjistrim nuk është më i disponueshëm.",
        sv = "Den här posten finns inte längre.",
        tr = "Bu kayıt artık mevcut değil.",
    ),
    "Estimated nutrition" to NomiTranslation(
        de = "Geschätzte Nährwerte", es = "Nutrición estimada", fr = "Nutrition estimée",
        it = "Valori nutrizionali stimati", nl = "Geschatte voedingswaarde",
        pt = "Nutrição estimada", sq = "Vlera ushqyese të vlerësuara",
        sv = "Uppskattad näring", tr = "Tahmini besin değerleri",
    ),
    "Nutrition" to NomiTranslation(
        de = "Nährwerte", es = "Nutrición", fr = "Nutrition", it = "Valori nutrizionali",
        nl = "Voedingswaarde", pt = "Nutrição", sq = "Vlerat ushqyese", sv = "Näring",
        tr = "Besin değerleri",
    ),
    "Calories" to NomiTranslation(
        de = "Kalorien", es = "Calorías", fr = "Calories", it = "Calorie", nl = "Calorieën",
        pt = "Calorias", sq = "Kalori", sv = "Kalorier", tr = "Kalori",
    ),
    "Carbohydrates" to NomiTranslation(
        de = "Kohlenhydrate", es = "Carbohidratos", fr = "Glucides", it = "Carboidrati",
        nl = "Koolhydraten", pt = "Hidratos de carbono", sq = "Karbohidrate",
        sv = "Kolhydrater", tr = "Karbonhidrat",
    ),
    "Source" to NomiTranslation(
        de = "Quelle", es = "Fuente", fr = "Source", it = "Fonte", nl = "Bron",
        pt = "Fonte", sq = "Burimi", sv = "Källa", tr = "Kaynak",
    ),

    // Health Connect
    "Optional health sync" to NomiTranslation(
        de = "Optionale Gesundheitssynchronisierung",
        es = "Sincronización de salud opcional",
        fr = "Synchronisation santé facultative",
        it = "Sincronizzazione salute facoltativa",
        nl = "Optionele gezondheidssynchronisatie",
        pt = "Sincronização de saúde opcional",
        sq = "Sinkronizim opsional i të dhënave shëndetësore",
        sv = "Valfri hälsosynkronisering",
        tr = "İsteğe bağlı sağlık eşitlemesi",
    ),
    "Nomi reads weight from the last 30 days, reads today's steps and active calories, and " +
        "writes back the weights you enter plus the calories, protein, carbs and fat of " +
        "everything you log." to NomiTranslation(
        de = "Nomi liest Gewichte der letzten 30 Tage, heutige Schritte und aktive Kalorien und " +
            "schreibt die Gewichte, die du einträgst, sowie Kalorien, Eiweiß, Kohlenhydrate und " +
            "Fett von allem, was du erfasst.",
        es = "Nomi lee los pesos de los últimos 30 días, los pasos y las calorías activas de hoy, " +
            "y escribe los pesos que introduces más las calorías, proteínas, carbohidratos y " +
            "grasas de todo lo que registras.",
        fr = "Nomi lit les poids des 30 derniers jours, les pas et les calories actives du jour, " +
            "et écrit les poids que tu saisis ainsi que les calories, protéines, glucides et " +
            "lipides de tout ce que tu enregistres.",
        it = "Nomi legge i pesi degli ultimi 30 giorni, i passi e le calorie attive di oggi e " +
            "scrive i pesi che inserisci più calorie, proteine, carboidrati e grassi di tutto " +
            "quello che registri.",
        nl = "Nomi leest gewichten van de laatste 30 dagen, de stappen en actieve calorieën van " +
            "vandaag, en schrijft de gewichten die je invoert plus de calorieën, eiwitten, " +
            "koolhydraten en vetten van alles wat je logt.",
        pt = "A Nomi lê os pesos dos últimos 30 dias, os passos e as calorias ativas de hoje, e " +
            "escreve os pesos que introduzes mais as calorias, proteínas, hidratos de carbono e " +
            "gorduras de tudo o que registas.",
        sq = "Nomi lexon peshat e 30 ditëve të fundit, hapat dhe kaloritë aktive të sotme, dhe " +
            "shkruan peshat që fut ti si edhe kaloritë, proteinat, karbohidratet dhe yndyrat e " +
            "gjithçkaje që regjistron.",
        sv = "Nomi läser vikter från de senaste 30 dagarna, dagens steg och aktiva kalorier, och " +
            "skriver tillbaka vikterna du anger plus kalorier, protein, kolhydrater och fett för " +
            "allt du loggar.",
        tr = "Nomi son 30 günün kilo kayıtlarını, bugünkü adımları ve aktif kalorileri okur; " +
            "girdiğin kilolarla birlikte kaydettiğin her şeyin kalori, protein, karbonhidrat ve " +
            "yağ değerlerini yazar.",
    ),
    "Health Connect isn't available on this device. Nomi works fully without it." to NomiTranslation(
        de = "Health Connect ist auf diesem Gerät nicht verfügbar. Nomi funktioniert auch ohne " +
            "Health Connect vollständig.",
        es = "Health Connect no está disponible en este dispositivo. Nomi funciona igual sin él.",
        fr = "Health Connect n’est pas disponible sur cet appareil. Nomi fonctionne pleinement " +
            "sans lui.",
        it = "Health Connect non è disponibile su questo dispositivo. Nomi funziona benissimo " +
            "anche senza.",
        nl = "Health Connect is niet beschikbaar op dit apparaat. Nomi werkt volledig zonder.",
        pt = "O Health Connect não está disponível neste dispositivo. A Nomi funciona na íntegra " +
            "sem ele.",
        sq = "Health Connect nuk ofrohet në këtë pajisje. Nomi funksionon plotësisht edhe pa të.",
        sv = "Health Connect är inte tillgängligt på den här enheten. Nomi fungerar fullt ut ändå.",
        tr = "Health Connect bu cihazda kullanılamıyor. Nomi onsuz da eksiksiz çalışır.",
    ),
    "Health Connect must be installed or updated before Nomi can connect." to NomiTranslation(
        de = "Health Connect muss installiert oder aktualisiert werden, bevor Nomi eine " +
            "Verbindung herstellen kann.",
        es = "Hay que instalar o actualizar Health Connect antes de que Nomi pueda conectarse.",
        fr = "Health Connect doit être installé ou mis à jour avant que Nomi puisse s’y connecter.",
        it = "Health Connect deve essere installato o aggiornato prima che Nomi possa collegarsi.",
        nl = "Health Connect moet worden geïnstalleerd of bijgewerkt voordat Nomi verbinding " +
            "kan maken.",
        pt = "O Health Connect tem de ser instalado ou atualizado antes de a Nomi poder ligar-se.",
        sq = "Health Connect duhet instaluar ose përditësuar para se Nomi të lidhet.",
        sv = "Health Connect måste installeras eller uppdateras innan Nomi kan ansluta.",
        tr = "Nomi bağlanabilmesi için Health Connect kurulmalı veya güncellenmeli.",
    ),
    "Nothing is shared until you approve all requested categories." to NomiTranslation(
        de = "Es werden keine Daten geteilt, bevor du alle angeforderten Kategorien freigibst.",
        es = "No se comparte nada hasta que apruebes todas las categorías solicitadas.",
        fr = "Rien n’est partagé tant que tu n’as pas approuvé toutes les catégories demandées.",
        it = "Non viene condiviso nulla finché non approvi tutte le categorie richieste.",
        nl = "Er wordt niets gedeeld totdat je alle gevraagde categorieën goedkeurt.",
        pt = "Nada é partilhado até aprovares todas as categorias pedidas.",
        sq = "Asgjë nuk ndahet derisa të miratosh të gjitha kategoritë e kërkuara.",
        sv = "Ingenting delas förrän du godkänner alla begärda kategorier.",
        tr = "İstenen tüm kategorileri onaylamadan hiçbir şey paylaşılmaz.",
    ),
    "Some permissions are missing. Approve every requested category to finish connecting." to
        NomiTranslation(
            de = "Einige Berechtigungen fehlen. Gib alle angeforderten Kategorien frei, um die " +
                "Verbindung abzuschließen.",
            es = "Faltan permisos. Aprueba todas las categorías solicitadas para terminar de " +
                "conectar.",
            fr = "Des autorisations manquent. Approuve toutes les catégories demandées pour " +
                "finaliser la connexion.",
            it = "Mancano alcune autorizzazioni. Approva tutte le categorie richieste per " +
                "completare il collegamento.",
            nl = "Er ontbreken machtigingen. Keur alle gevraagde categorieën goed om de " +
                "verbinding af te ronden.",
            pt = "Faltam permissões. Aprova todas as categorias pedidas para concluir a ligação.",
            sq = "Disa leje mungojnë. Mirato të gjitha kategoritë e kërkuara për ta përfunduar " +
                "lidhjen.",
            sv = "Vissa behörigheter saknas. Godkänn alla begärda kategorier för att slutföra " +
                "anslutningen.",
            tr = "Bazı izinler eksik. Bağlantıyı tamamlamak için istenen tüm kategorileri onayla.",
        ),
    "Connected. You can change access at any time in Health Connect." to NomiTranslation(
        de = "Verbunden. Du kannst den Zugriff jederzeit in Health Connect ändern.",
        es = "Conectado. Puedes cambiar el acceso cuando quieras en Health Connect.",
        fr = "Connecté. Tu peux modifier l’accès à tout moment dans Health Connect.",
        it = "Collegato. Puoi cambiare l’accesso quando vuoi in Health Connect.",
        nl = "Verbonden. Je kunt de toegang altijd wijzigen in Health Connect.",
        pt = "Ligado. Podes alterar o acesso a qualquer momento no Health Connect.",
        sq = "I lidhur. Mund ta ndryshosh aksesin në çdo kohë te Health Connect.",
        sv = "Ansluten. Du kan ändra åtkomsten när som helst i Health Connect.",
        tr = "Bağlandı. Erişimi istediğin zaman Health Connect’ten değiştirebilirsin.",
    ),
    "Syncing health data..." to NomiTranslation(
        de = "Gesundheitsdaten werden synchronisiert...",
        es = "Sincronizando datos de salud...",
        fr = "Synchronisation des données de santé...",
        it = "Sincronizzazione dei dati sulla salute...",
        nl = "Gezondheidsgegevens synchroniseren...",
        pt = "A sincronizar dados de saúde...",
        sq = "Po sinkronizohen të dhënat shëndetësore...",
        sv = "Synkroniserar hälsodata...",
        tr = "Sağlık verileri eşitleniyor...",
    ),
    "Today's activity" to NomiTranslation(
        de = "Heutige Aktivität", es = "Actividad de hoy", fr = "Activité du jour",
        it = "Attività di oggi", nl = "Activiteit van vandaag", pt = "Atividade de hoje",
        sq = "Aktiviteti i sotëm", sv = "Dagens aktivitet", tr = "Bugünkü aktivite",
    ),
    "Steps" to NomiTranslation(
        de = "Schritte", es = "Pasos", fr = "Pas", it = "Passi", nl = "Stappen",
        pt = "Passos", sq = "Hapa", sv = "Steg", tr = "Adım",
    ),
    "Not synced yet" to NomiTranslation(
        de = "Noch nicht synchronisiert", es = "Aún sin sincronizar", fr = "Pas encore synchronisé",
        it = "Non ancora sincronizzato", nl = "Nog niet gesynchroniseerd",
        pt = "Ainda não sincronizado", sq = "Ende i pasinkronizuar", sv = "Inte synkroniserat än",
        tr = "Henüz eşitlenmedi",
    ),
    "Active calories" to NomiTranslation(
        de = "Aktive Kalorien", es = "Calorías activas", fr = "Calories actives",
        it = "Calorie attive", nl = "Actieve calorieën", pt = "Calorias ativas",
        sq = "Kalori aktive", sv = "Aktiva kalorier", tr = "Aktif kalori",
    ),
    "Food entries shared" to NomiTranslation(
        de = "Geteilte Ernährungseinträge", es = "Registros de comida compartidos",
        fr = "Entrées alimentaires partagées", it = "Voci alimentari condivise",
        nl = "Gedeelde voedingsitems", pt = "Registos de comida partilhados",
        sq = "Regjistrime ushqimore të ndara", sv = "Delade matposter",
        tr = "Paylaşılan besin kaydı",
    ),
    "Sync now" to NomiTranslation(
        de = "Jetzt synchronisieren", es = "Sincronizar ahora", fr = "Synchroniser maintenant",
        it = "Sincronizza ora", nl = "Nu synchroniseren", pt = "Sincronizar agora",
        sq = "Sinkronizo tani", sv = "Synkronisera nu", tr = "Şimdi eşitle",
    ),
    "Complete permissions" to NomiTranslation(
        de = "Berechtigungen vervollständigen", es = "Completar permisos",
        fr = "Compléter les autorisations", it = "Completa le autorizzazioni",
        nl = "Machtigingen aanvullen", pt = "Concluir permissões",
        sq = "Plotëso lejet", sv = "Slutför behörigheter", tr = "İzinleri tamamla",
    ),
    "Choose permissions" to NomiTranslation(
        de = "Berechtigungen auswählen", es = "Elegir permisos", fr = "Choisir les autorisations",
        it = "Scegli le autorizzazioni", nl = "Machtigingen kiezen", pt = "Escolher permissões",
        sq = "Zgjidh lejet", sv = "Välj behörigheter", tr = "İzinleri seç",
    ),
    "Health data is used only for your local Nomi experience and is never sold." to NomiTranslation(
        de = "Gesundheitsdaten werden nur für deine lokale Nomi-Nutzung verwendet und niemals " +
            "verkauft.",
        es = "Los datos de salud solo se usan en tu Nomi local y nunca se venden.",
        fr = "Les données de santé servent uniquement à ton usage local de Nomi et ne sont " +
            "jamais vendues.",
        it = "I dati sulla salute servono solo alla tua esperienza locale in Nomi e non vengono " +
            "mai venduti.",
        nl = "Gezondheidsgegevens worden alleen lokaal in Nomi gebruikt en nooit verkocht.",
        pt = "Os dados de saúde são usados apenas na tua Nomi local e nunca são vendidos.",
        sq = "Të dhënat shëndetësore përdoren vetëm për përvojën tënde lokale në Nomi dhe nuk " +
            "shiten kurrë.",
        sv = "Hälsodata används bara lokalt i Nomi och säljs aldrig.",
        tr = "Sağlık verileri yalnızca cihazındaki Nomi deneyimin için kullanılır ve asla satılmaz.",
    ),

    // AI debug screen
    "AI debug" to NomiTranslation(
        de = "KI-Debug", es = "Depuración de IA", fr = "Débogage IA", it = "Debug IA",
        nl = "AI-debug", pt = "Depuração de IA", sq = "Korrigjimi i IA-së", sv = "AI-felsökning",
        tr = "YZ hata ayıklama",
    ),
    "Privacy-safe diagnostics" to NomiTranslation(
        de = "Datenschutzfreundliche Diagnose", es = "Diagnóstico respetuoso con la privacidad",
        fr = "Diagnostics respectueux de la vie privée", it = "Diagnostica rispettosa della privacy",
        nl = "Privacyveilige diagnostiek", pt = "Diagnóstico respeitador da privacidade",
        sq = "Diagnostikë që respekton privatësinë", sv = "Integritetssäker diagnostik",
        tr = "Gizliliğe saygılı tanılama",
    ),
    "Events contain provider, model, timing, cache and validation status—never API keys or " +
        "request headers." to NomiTranslation(
        de = "Ereignisse enthalten Anbieter, Modell, Dauer, Cache- und Prüfstatus – niemals " +
            "API-Schlüssel oder Anfrage-Header.",
        es = "Los eventos incluyen proveedor, modelo, tiempos, caché y estado de validación; " +
            "nunca claves de API ni cabeceras de la petición.",
        fr = "Les événements contiennent le fournisseur, le modèle, les durées, le cache et le " +
            "statut de validation — jamais de clés d’API ni d’en-têtes de requête.",
        it = "Gli eventi contengono provider, modello, tempi, cache e stato di validazione: mai " +
            "chiavi API o intestazioni delle richieste.",
        nl = "Gebeurtenissen bevatten provider, model, timing, cache en validatiestatus — nooit " +
            "API-sleutels of request-headers.",
        pt = "Os eventos contêm fornecedor, modelo, tempos, cache e estado de validação — nunca " +
            "chaves de API nem cabeçalhos do pedido.",
        sq = "Ngjarjet përmbajnë ofruesin, modelin, kohën, cache-n dhe statusin e verifikimit – " +
            "kurrë çelësa API ose header-a kërkesash.",
        sv = "Händelser innehåller leverantör, modell, tider, cache och valideringsstatus – " +
            "aldrig API-nycklar eller förfrågningsrubriker.",
        tr = "Olaylar sağlayıcı, model, süre, önbellek ve doğrulama durumunu içerir; API " +
            "anahtarlarını veya istek başlıklarını asla içermez.",
    ),
    "Disable debug events" to NomiTranslation(
        de = "Debug-Ereignisse deaktivieren", es = "Desactivar eventos de depuración",
        fr = "Désactiver les événements de débogage", it = "Disattiva gli eventi di debug",
        nl = "Debug-gebeurtenissen uitschakelen", pt = "Desativar eventos de depuração",
        sq = "Çaktivizo ngjarjet e korrigjimit", sv = "Stäng av felsökningshändelser",
        tr = "Hata ayıklama olaylarını kapat",
    ),
    "Enable debug events" to NomiTranslation(
        de = "Debug-Ereignisse aktivieren", es = "Activar eventos de depuración",
        fr = "Activer les événements de débogage", it = "Attiva gli eventi di debug",
        nl = "Debug-gebeurtenissen inschakelen", pt = "Ativar eventos de depuração",
        sq = "Aktivizo ngjarjet e korrigjimit", sv = "Slå på felsökningshändelser",
        tr = "Hata ayıklama olaylarını aç",
    ),
    "No debug events recorded." to NomiTranslation(
        de = "Noch keine Debug-Ereignisse aufgezeichnet.",
        es = "No se han registrado eventos de depuración.",
        fr = "Aucun événement de débogage enregistré.",
        it = "Nessun evento di debug registrato.",
        nl = "Geen debug-gebeurtenissen vastgelegd.",
        pt = "Nenhum evento de depuração registado.",
        sq = "Nuk ka ngjarje korrigjimi të regjistruara.",
        sv = "Inga felsökningshändelser registrerade.",
        tr = "Kayıtlı hata ayıklama olayı yok.",
    ),
)
