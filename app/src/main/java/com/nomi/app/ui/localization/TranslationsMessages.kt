package com.nomi.app.ui.localization

/** Provider credentials, backup workflow and app-level validation messages. */
internal val messageTranslations: Map<String, NomiTranslation> = mapOf(
    "{0} (stored securely)" to NomiTranslation(
        de = "{0} (sicher gespeichert)", es = "{0} (guardada de forma segura)", fr = "{0} (stockée en sécurité)", it = "{0} (salvata in modo sicuro)", nl = "{0} (veilig opgeslagen)", pt = "{0} (guardada em segurança)", sq = "{0} (ruajtur në mënyrë të sigurt)", sv = "{0} (säkert lagrad)", tr = "{0} (güvenle saklanıyor)",
    ),
    "Google Gemini API key" to NomiTranslation(
        de = "Google-Gemini-API-Schlüssel", es = "Clave de API de Google Gemini", fr = "Clé API Google Gemini", it = "Chiave API Google Gemini", nl = "Google Gemini-API-sleutel", pt = "Chave da API Google Gemini", sq = "Çelësi API i Google Gemini", sv = "Google Gemini API-nyckel", tr = "Google Gemini API anahtarı",
    ),
    "Exa API key" to NomiTranslation(
        de = "Exa-API-Schlüssel", es = "Clave de API de Exa", fr = "Clé API Exa", it = "Chiave API Exa", nl = "Exa-API-sleutel", pt = "Chave da API Exa", sq = "Çelësi API i Exa", sv = "Exa API-nyckel", tr = "Exa API anahtarı",
    ),
    "Exa retrieves sources through Exa's official API; Gemini runs directly through Google's Gemini API. Both keys stay encrypted on this device." to NomiTranslation(
        de = "Exa ruft Quellen über die offizielle Exa-API ab; Gemini läuft direkt über Googles Gemini-API. Beide Schlüssel bleiben auf diesem Gerät verschlüsselt.", es = "Exa obtiene fuentes mediante su API oficial; Gemini funciona directamente con la API de Google Gemini. Ambas claves permanecen cifradas en este dispositivo.", fr = "Exa récupère les sources via son API officielle ; Gemini utilise directement l’API Google Gemini. Les deux clés restent chiffrées sur cet appareil.", it = "Exa recupera le fonti tramite la sua API ufficiale; Gemini usa direttamente l’API Google Gemini. Entrambe le chiavi restano cifrate sul dispositivo.", nl = "Exa haalt bronnen op via de officiële Exa-API; Gemini werkt rechtstreeks via de Google Gemini-API. Beide sleutels blijven versleuteld op dit apparaat.", pt = "A Exa obtém fontes através da API oficial; o Gemini funciona diretamente pela API Google Gemini. Ambas as chaves ficam encriptadas neste dispositivo.", sq = "Exa merr burimet përmes API-së zyrtare; Gemini funksionon drejtpërdrejt përmes API-së Google Gemini. Të dy çelësat mbeten të enkriptuar në këtë pajisje.", sv = "Exa hämtar källor via Exas officiella API; Gemini körs direkt via Google Gemini API. Båda nycklarna förblir krypterade på enheten.", tr = "Exa kaynakları resmi Exa API üzerinden alır; Gemini doğrudan Google Gemini API üzerinden çalışır. İki anahtar da bu cihazda şifreli kalır.",
    ),
    "The selected document could not be opened" to NomiTranslation(
        de = "Das ausgewählte Dokument konnte nicht geöffnet werden", es = "No se pudo abrir el documento seleccionado", fr = "Impossible d’ouvrir le document sélectionné", it = "Impossibile aprire il documento selezionato", nl = "Het geselecteerde document kon niet worden geopend", pt = "Não foi possível abrir o documento selecionado", sq = "Dokumenti i zgjedhur nuk mund të hapej", sv = "Det valda dokumentet kunde inte öppnas", tr = "Seçilen belge açılamadı",
    ),
    "Backup exported" to NomiTranslation(
        de = "Sicherung exportiert", es = "Copia de seguridad exportada", fr = "Sauvegarde exportée", it = "Backup esportato", nl = "Back-up geëxporteerd", pt = "Cópia de segurança exportada", sq = "Kopja rezervë u eksportua", sv = "Säkerhetskopian exporterades", tr = "Yedek dışa aktarıldı",
    ),
    "Nomi couldn't export the backup" to NomiTranslation(
        de = "Nomi konnte die Sicherung nicht exportieren", es = "Nomi no pudo exportar la copia de seguridad", fr = "Nomi n’a pas pu exporter la sauvegarde", it = "Nomi non è riuscito a esportare il backup", nl = "Nomi kon de back-up niet exporteren", pt = "A Nomi não conseguiu exportar a cópia de segurança", sq = "Nomi nuk mundi ta eksportonte kopjen rezervë", sv = "Nomi kunde inte exportera säkerhetskopian", tr = "Nomi yedeği dışa aktaramadı",
    ),
    "That isn't a valid Nomi backup" to NomiTranslation(
        de = "Das ist keine gültige Nomi-Sicherung", es = "No es una copia de seguridad válida de Nomi", fr = "Ce n’est pas une sauvegarde Nomi valide", it = "Non è un backup Nomi valido", nl = "Dit is geen geldige Nomi-back-up", pt = "Não é uma cópia de segurança válida da Nomi", sq = "Kjo nuk është një kopje rezervë e vlefshme e Nomi", sv = "Det är inte en giltig Nomi-säkerhetskopia", tr = "Bu geçerli bir Nomi yedeği değil",
    ),
    "Notification permission is needed for reminders" to NomiTranslation(
        de = "Für Erinnerungen ist die Benachrichtigungsberechtigung erforderlich", es = "Se necesita permiso de notificaciones para los recordatorios", fr = "L’autorisation de notification est nécessaire pour les rappels", it = "Per i promemoria serve l’autorizzazione alle notifiche", nl = "Voor herinneringen is toestemming voor meldingen nodig", pt = "É necessária permissão de notificações para os lembretes", sq = "Për kujtesat nevojitet leja për njoftime", sv = "Aviseringsbehörighet krävs för påminnelser", tr = "Hatırlatıcılar için bildirim izni gerekir",
    ),
    "Replace local Nomi data?" to NomiTranslation(
        de = "Lokale Nomi-Daten ersetzen?", es = "¿Sustituir los datos locales de Nomi?", fr = "Remplacer les données Nomi locales ?", it = "Sostituire i dati Nomi locali?", nl = "Lokale Nomi-gegevens vervangen?", pt = "Substituir os dados locais da Nomi?", sq = "Të zëvendësohen të dhënat lokale të Nomi?", sv = "Ersätta lokala Nomi-data?", tr = "Yerel Nomi verileri değiştirilsin mi?",
    ),
    "API keys stay on this device and are never imported." to NomiTranslation(
        de = "API-Schlüssel bleiben auf diesem Gerät und werden nie importiert.", es = "Las claves de API permanecen en este dispositivo y nunca se importan.", fr = "Les clés API restent sur cet appareil et ne sont jamais importées.", it = "Le chiavi API restano su questo dispositivo e non vengono mai importate.", nl = "API-sleutels blijven op dit apparaat en worden nooit geïmporteerd.", pt = "As chaves de API ficam neste dispositivo e nunca são importadas.", sq = "Çelësat API mbeten në këtë pajisje dhe nuk importohen kurrë.", sv = "API-nycklar stannar på enheten och importeras aldrig.", tr = "API anahtarları bu cihazda kalır ve asla içe aktarılmaz.",
    ),
    "Replace data" to NomiTranslation(
        de = "Daten ersetzen", es = "Sustituir datos", fr = "Remplacer les données", it = "Sostituisci dati", nl = "Gegevens vervangen", pt = "Substituir dados", sq = "Zëvendëso të dhënat", sv = "Ersätt data", tr = "Verileri değiştir",
    ),
    "Backup restored" to NomiTranslation(
        de = "Sicherung wiederhergestellt", es = "Copia de seguridad restaurada", fr = "Sauvegarde restaurée", it = "Backup ripristinato", nl = "Back-up hersteld", pt = "Cópia de segurança restaurada", sq = "Kopja rezervë u rikthye", sv = "Säkerhetskopian återställdes", tr = "Yedek geri yüklendi",
    ),
    "Nomi couldn't restore that backup" to NomiTranslation(
        de = "Nomi konnte diese Sicherung nicht wiederherstellen", es = "Nomi no pudo restaurar esa copia de seguridad", fr = "Nomi n’a pas pu restaurer cette sauvegarde", it = "Nomi non è riuscito a ripristinare il backup", nl = "Nomi kon die back-up niet herstellen", pt = "A Nomi não conseguiu restaurar essa cópia de segurança", sq = "Nomi nuk mundi ta rikthente atë kopje rezervë", sv = "Nomi kunde inte återställa säkerhetskopian", tr = "Nomi bu yedeği geri yükleyemedi",
    ),
    "Food logs" to NomiTranslation(
        de = "Lebensmittelprotokolle", es = "Registros de alimentos", fr = "Journaux alimentaires", it = "Registri alimentari", nl = "Voedingslogboeken", pt = "Registos alimentares", sq = "Regjistrimet e ushqimit", sv = "Matloggar", tr = "Yemek kayıtları",
    ),
    "Foods" to NomiTranslation(
        de = "Lebensmittel", es = "Alimentos", fr = "Aliments", it = "Alimenti", nl = "Voedingsmiddelen", pt = "Alimentos", sq = "Ushqimet", sv = "Livsmedel", tr = "Yiyecekler",
    ),
    "Weights" to NomiTranslation(
        de = "Gewichte", es = "Pesos", fr = "Poids", it = "Pesi", nl = "Gewichten", pt = "Pesos", sq = "Peshat", sv = "Vikter", tr = "Kilolar",
    ),
    "Plans" to NomiTranslation(
        de = "Pläne", es = "Planes", fr = "Plans", it = "Piani", nl = "Plannen", pt = "Planos", sq = "Planet", sv = "Planer", tr = "Planlar",
    ),
    "Nomi couldn't save your plan. Please try again." to NomiTranslation(
        de = "Nomi konnte deinen Plan nicht speichern. Bitte versuche es erneut.", es = "Nomi no pudo guardar tu plan. Inténtalo de nuevo.", fr = "Nomi n’a pas pu enregistrer ton plan. Réessaie.", it = "Nomi non è riuscito a salvare il piano. Riprova.", nl = "Nomi kon je plan niet opslaan. Probeer het opnieuw.", pt = "A Nomi não conseguiu guardar o teu plano. Tenta novamente.", sq = "Nomi nuk mundi ta ruante planin. Provo përsëri.", sv = "Nomi kunde inte spara din plan. Försök igen.", tr = "Nomi planını kaydedemedi. Lütfen tekrar dene.",
    ),
    "Enter an amount greater than zero" to NomiTranslation(
        de = "Gib eine Menge größer als null ein", es = "Introduce una cantidad mayor que cero", fr = "Saisis une quantité supérieure à zéro", it = "Inserisci una quantità maggiore di zero", nl = "Voer een hoeveelheid groter dan nul in", pt = "Introduz uma quantidade superior a zero", sq = "Vendos një sasi më të madhe se zero", sv = "Ange en mängd större än noll", tr = "Sıfırdan büyük bir miktar gir",
    ),
    "Save this food again before favoriting it" to NomiTranslation(
        de = "Speichere dieses Lebensmittel erneut, bevor du es als Favorit markierst", es = "Guarda de nuevo este alimento antes de marcarlo como favorito", fr = "Enregistre à nouveau cet aliment avant de l’ajouter aux favoris", it = "Salva di nuovo l’alimento prima di aggiungerlo ai preferiti", nl = "Sla dit voedingsmiddel opnieuw op voordat je het als favoriet markeert", pt = "Guarda novamente este alimento antes de o marcares como favorito", sq = "Ruaje përsëri këtë ushqim para se ta shënosh si të preferuar", sv = "Spara livsmedlet igen innan du favoritmarkerar det", tr = "Favorilere eklemeden önce bu yiyeceği yeniden kaydet",
    ),
    "Nomi couldn't delete that food." to NomiTranslation(
        de = "Nomi konnte dieses Lebensmittel nicht löschen.", es = "Nomi no pudo eliminar ese alimento.", fr = "Nomi n’a pas pu supprimer cet aliment.", it = "Nomi non è riuscito a eliminare l’alimento.", nl = "Nomi kon dat voedingsmiddel niet verwijderen.", pt = "A Nomi não conseguiu eliminar esse alimento.", sq = "Nomi nuk mundi ta fshinte atë ushqim.", sv = "Nomi kunde inte radera livsmedlet.", tr = "Nomi bu yiyeceği silemedi.",
    ),
    "Nomi couldn't restore that food." to NomiTranslation(
        de = "Nomi konnte dieses Lebensmittel nicht wiederherstellen.", es = "Nomi no pudo restaurar ese alimento.", fr = "Nomi n’a pas pu restaurer cet aliment.", it = "Nomi non è riuscito a ripristinare l’alimento.", nl = "Nomi kon dat voedingsmiddel niet herstellen.", pt = "A Nomi não conseguiu restaurar esse alimento.", sq = "Nomi nuk mundi ta rikthente atë ushqim.", sv = "Nomi kunde inte återställa livsmedlet.", tr = "Nomi bu yiyeceği geri yükleyemedi.",
    ),
    "Nomi couldn't save that meal" to NomiTranslation(
        de = "Nomi konnte diese Mahlzeit nicht speichern", es = "Nomi no pudo guardar esa comida", fr = "Nomi n’a pas pu enregistrer ce repas", it = "Nomi non è riuscito a salvare il pasto", nl = "Nomi kon die maaltijd niet opslaan", pt = "A Nomi não conseguiu guardar essa refeição", sq = "Nomi nuk mundi ta ruante atë vakt", sv = "Nomi kunde inte spara måltiden", tr = "Nomi bu öğünü kaydedemedi",
    ),
    "That food is no longer available" to NomiTranslation(
        de = "Dieses Lebensmittel ist nicht mehr verfügbar", es = "Ese alimento ya no está disponible", fr = "Cet aliment n’est plus disponible", it = "L’alimento non è più disponibile", nl = "Dat voedingsmiddel is niet meer beschikbaar", pt = "Esse alimento já não está disponível", sq = "Ai ushqim nuk është më i disponueshëm", sv = "Livsmedlet är inte längre tillgängligt", tr = "Bu yiyecek artık kullanılamıyor",
    ),
    "Nomi couldn't copy that day." to NomiTranslation(
        de = "Nomi konnte diesen Tag nicht kopieren.", es = "Nomi no pudo copiar ese día.", fr = "Nomi n’a pas pu copier cette journée.", it = "Nomi non è riuscito a copiare quel giorno.", nl = "Nomi kon die dag niet kopiëren.", pt = "A Nomi não conseguiu copiar esse dia.", sq = "Nomi nuk mundi ta kopjonte atë ditë.", sv = "Nomi kunde inte kopiera dagen.", tr = "Nomi bu günü kopyalayamadı.",
    ),
    "Enter a valid weight." to NomiTranslation(
        de = "Gib ein gültiges Gewicht ein.", es = "Introduce un peso válido.", fr = "Saisis un poids valide.", it = "Inserisci un peso valido.", nl = "Voer een geldig gewicht in.", pt = "Introduz um peso válido.", sq = "Vendos një peshë të vlefshme.", sv = "Ange en giltig vikt.", tr = "Geçerli bir kilo gir.",
    ),
    "Nomi couldn't add that item." to NomiTranslation(
        de = "Nomi konnte diesen Eintrag nicht hinzufügen.", es = "Nomi no pudo añadir ese elemento.", fr = "Nomi n’a pas pu ajouter cet élément.", it = "Nomi non è riuscito ad aggiungere l’elemento.", nl = "Nomi kon dat item niet toevoegen.", pt = "A Nomi não conseguiu adicionar esse item.", sq = "Nomi nuk mundi ta shtonte atë artikull.", sv = "Nomi kunde inte lägga till posten.", tr = "Nomi bu öğeyi ekleyemedi.",
    ),
    "Check the nutrition target values." to NomiTranslation(
        de = "Überprüfe die Nährwertziele.", es = "Comprueba los valores de los objetivos nutricionales.", fr = "Vérifie les valeurs des objectifs nutritionnels.", it = "Controlla i valori degli obiettivi nutrizionali.", nl = "Controleer de voedingsdoelen.", pt = "Verifica os valores das metas nutricionais.", sq = "Kontrollo vlerat e objektivave ushqyese.", sv = "Kontrollera värdena för näringsmålen.", tr = "Beslenme hedefi değerlerini kontrol et.",
    ),
    "Nomi couldn't save those targets." to NomiTranslation(
        de = "Nomi konnte diese Ziele nicht speichern.", es = "Nomi no pudo guardar esos objetivos.", fr = "Nomi n’a pas pu enregistrer ces objectifs.", it = "Nomi non è riuscito a salvare quegli obiettivi.", nl = "Nomi kon die doelen niet opslaan.", pt = "A Nomi não conseguiu guardar essas metas.", sq = "Nomi nuk mundi t’i ruante ato objektiva.", sv = "Nomi kunde inte spara målen.", tr = "Nomi bu hedefleri kaydedemedi.",
    ),
    "Nomi couldn't recalculate that profile." to NomiTranslation(
        de = "Nomi konnte dieses Profil nicht neu berechnen.", es = "Nomi no pudo recalcular ese perfil.", fr = "Nomi n’a pas pu recalculer ce profil.", it = "Nomi non è riuscito a ricalcolare il profilo.", nl = "Nomi kon dat profiel niet opnieuw berekenen.", pt = "A Nomi não conseguiu recalcular esse perfil.", sq = "Nomi nuk mundi ta rillogariste atë profil.", sv = "Nomi kunde inte räkna om profilen.", tr = "Nomi bu profili yeniden hesaplayamadı.",
    ),
)
