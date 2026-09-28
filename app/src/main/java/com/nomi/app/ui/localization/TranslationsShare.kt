package com.nomi.app.ui.localization

/**
 * Sharing a day with another phone by holding the two together, reached from a food row's
 * long-press menu.
 *
 * The strings are split by stage: what the row menu offers, what each phone says while it waits to
 * be touched, and what is said once a day has arrived or failed to. The last group is read as a
 * message rather than shown in the menu, so it is worded as an outcome.
 */
internal val shareTranslations: Map<String, NomiTranslation> = mapOf(
    "Share" to NomiTranslation(
        de = "Teilen", es = "Compartir", fr = "Partager", it = "Condividi",
        nl = "Delen", pt = "Partilhar", sq = "Ndaj", sv = "Dela", tr = "Paylaş",
    ),
    "Receive a shared day" to NomiTranslation(
        de = "Einen geteilten Tag empfangen", es = "Recibir un día compartido",
        fr = "Recevoir une journée partagée", it = "Ricevi una giornata condivisa",
        nl = "Een gedeelde dag ontvangen", pt = "Receber um dia partilhado",
        sq = "Merr një ditë të ndarë", sv = "Ta emot en delad dag",
        tr = "Paylaşılan gün al",
    ),
    "Include totals" to NomiTranslation(
        de = "Summen einbeziehen", es = "Incluir totales", fr = "Inclure les totaux",
        it = "Includi i totali", nl = "Totalen meesturen", pt = "Incluir totais",
        sq = "Përfshi shumatat", sv = "Inkludera summor", tr = "Toplamları dahil et",
    ),
    "{0} in total" to NomiTranslation(
        de = "{0} insgesamt", es = "{0} en total", fr = "{0} au total", it = "{0} in totale",
        nl = "{0} in totaal", pt = "{0} no total", sq = "{0} gjithsej", sv = "{0} totalt",
        tr = "toplam {0}",
    ),
    "Tap to share" to NomiTranslation(
        de = "Zum Teilen antippen", es = "Toca para compartir",
        fr = "Touchez pour partager", it = "Tocca per condividere",
        nl = "Tik om te delen", pt = "Toque para partilhar", sq = "Prekni për ta ndarë",
        sv = "Tryck för att dela", tr = "Paylaşmak için dokun",
    ),

    // What each phone says while it waits to be touched. The instruction has to say which phone
    // does what, because holding them together the wrong way round is the first thing anyone tries.
    "Ready to share" to NomiTranslation(
        de = "Bereit zum Teilen", es = "Listo para compartir", fr = "Prêt à partager",
        it = "Pronto da condividere", nl = "Klaar om te delen", pt = "Pronto para partilhar",
        sq = "Gati për t'u ndarë", sv = "Redo att dela", tr = "Paylaşmaya hazır",
    ),
    "Hold the other phone to the back of this one to send {0} foods" to NomiTranslation(
        de = "Halte das andere Telefon an die Rückseite dieses, um {0} Lebensmittel zu senden",
        es = "Acerca el otro teléfono a la parte trasera de este para enviar {0} alimentos",
        fr = "Approchez l'autre téléphone de l'arrière de celui-ci pour envoyer {0} aliments",
        it = "Avvicina l'altro telefono al retro di questo per inviare {0} alimenti",
        nl = "Houd de andere telefoon tegen de achterkant van deze om {0} voedingsmiddelen te sturen",
        pt = "Aproxime o outro telefone da parte de trás deste para enviar {0} alimentos",
        sq = "Mbaj telefonin tjetër pas pjesës së këtij për të dërguar {0} ushqime",
        sv = "Håll den andra telefonen mot baksidan av den här för att skicka {0} livsmedel",
        tr = "{0} yiyeceği göndermek için diğer telefonu bunun arkasına dayayın",
    ),
    "The other phone needs Nomi open and unlocked" to NomiTranslation(
        de = "Das andere Telefon braucht geöffnetes und entsperrtes Nomi",
        es = "El otro teléfono necesita Nomi abierto y desbloqueado",
        fr = "L'autre téléphone a besoin de Nomi ouvert et déverrouillé",
        it = "L'altro telefono ha bisogno di Nomi aperto e sbloccato",
        nl = "De andere telefoon heeft Nomi open en ontgrendeld nodig",
        pt = "O outro telefone precisa do Nomi aberto e desbloqueado",
        sq = "Telefoni tjetër ka nevojë për Nomi të hapur dhe të zhbllokuar",
        sv = "Den andra telefonen behöver Nomi öppet och upplåst",
        tr = "Diğer telefonda Nomi açık ve kilidi açık olmalı",
    ),
    "Waiting for a phone" to NomiTranslation(
        de = "Warte auf ein Telefon", es = "Esperando un teléfono",
        fr = "En attente d'un téléphone", it = "In attesa di un telefono",
        nl = "Wachten op een telefoon", pt = "À espera de um telefone",
        sq = "Po pret një telefon", sv = "Väntar på en telefon", tr = "Bir telefon bekleniyor",
    ),
    "Hold the sending phone to the back of this one to receive its day" to NomiTranslation(
        de = "Halte das sendende Telefon an die Rückseite dieses, um den Tag zu empfangen",
        es = "Acerca el teléfono que envía a la parte trasera de este para recibir su día",
        fr = "Approchez le téléphone qui envoie de l'arrière de celui-ci pour recevoir sa journée",
        it = "Avvicina il telefono che invia al retro di questo per ricevere la sua giornata",
        nl = "Houd het verzendende apparaat tegen de achterkant van deze om de dag te ontvangen",
        pt = "Aproxime o telefone que envia da parte de trás deste para receber o dia",
        sq = "Mbaj telefonin dërgesë pas pjesës së këtij për të marrë ditën e tij",
        sv = "Håll telefonen som skickar mot baksidan av den här för att ta emot dagen",
        tr = "Gönderen telefonu bunun arkasına dayayarak günü al",
    ),
    "Keep both phones still until the day has arrived" to NomiTranslation(
        de = "Halte beide Telefone still, bis der Tag angekommen ist",
        es = "Mantén ambos teléfonos quietos hasta que llegue el día",
        fr = "Gardez les deux téléphones immobiles jusqu'à l'arrivée de la journée",
        it = "Tieni fermi entrambi i telefoni finché la giornata non è arrivata",
        nl = "Houd beide telefoons stil tot de dag is aangekomen",
        pt = "Mantenha os dois telefones parados até o dia chegar",
        sq = "Mbani të dy telefonat pa lëvizur derisa të arrijë dita",
        sv = "Håll båda telefoner stilla tills dagen har kommit",
        tr = "Gün gelene kadar iki telefonu sabit tutun",
    ),
    "Looking for a phone" to NomiTranslation(
        de = "Suche nach einem Telefon", es = "Buscando un teléfono",
        fr = "Recherche d'un téléphone", it = "Ricerca di un telefono",
        nl = "Zoeken naar een telefoon", pt = "À procura de um telefone",
        sq = "Po kërkoj një telefon", sv = "Söker efter en telefon", tr = "Bir telefon aranıyor",
    ),

    // "Cancel" and "Discard" are already in the catalogue from the dialogs and rows that use
    // them, and one meaning per word is the point: a button that says one thing in one place and
    // another in another is worse than either.

    // The day that arrived, shown before it is written to the diary. A file that has crossed a
    // radio is not yet a fact about this phone's user, so this is where the user decides.
    "A day arrived" to NomiTranslation(
        de = "Ein Tag ist angekommen", es = "Ha llegado un día", fr = "Une journée est arrivée",
        it = "È arrivata una giornata", nl = "Een dag is aangekomen", pt = "Chegou um dia",
        sq = "Erdh një ditë", sv = "En dag har kommit", tr = "Bir gün geldi",
    ),
    "Shared from another Nomi, eaten on {0}" to NomiTranslation(
        de = "Aus einem anderen Nomi geteilt, gegessen am {0}",
        es = "Compartido desde otro Nomi, comido el {0}",
        fr = "Partagé depuis un autre Nomi, mangé le {0}",
        it = "Condiviso da un altro Nomi, mangiato il {0}",
        nl = "Gedeeld vanaf een andere Nomi, gegeten op {0}",
        pt = "Partilhado a partir de outro Nomi, comido a {0}",
        sq = "Ndarë nga një Nomi tjetër, ngrënë më {0}",
        sv = "Delad från en annan Nomi, äten {0}",
        tr = "Başka bir Nomi'den paylaşıldı, {0} tarihinde yendi",
    ),
    "Protein {0} · carbs {1} · fat {2} g" to NomiTranslation(
        de = "Eiweiß {0} · Kohlenhydrate {1} · Fett {2} g",
        es = "Proteína {0} · carbohidratos {1} · grasa {2} g",
        fr = "Protéines {0} · glucides {1} · lipides {2} g",
        it = "Proteine {0} · carboidrati {1} · grassi {2} g",
        nl = "Eiwit {0} · koolhydraten {1} · vet {2} g",
        pt = "Proteína {0} · hidratos {1} · gordura {2} g",
        sq = "Proteinë {0} · karbohydrate {1} · yndyrë {2} g",
        sv = "Protein {0} · kolhydrat {1} · fett {2} g",
        tr = "Protein {0} · karbonhidrat {1} · yağ {2} g",
    ),
    "{0} across {1} foods" to NomiTranslation(
        de = "{0} bei {1} Lebensmitteln", es = "{0} en {1} alimentos",
        fr = "{0} sur {1} aliments", it = "{0} su {1} alimenti",
        nl = "{0} over {1} voedingsmiddelen", pt = "{0} em {1} alimentos",
        sq = "{0} në {1} ushqime", sv = "{0} över {1} livsmedel", tr = "{1} yiyeçekte {0}",
    ),
    "Add to my diary" to NomiTranslation(
        de = "Zu meinem Tagebuch hinzufügen", es = "Añadir a mi diario",
        fr = "Ajouter à mon journal", it = "Aggiungi al mio diario", nl = "Aan mijn dagboek toevoegen",
        pt = "Adicionar ao meu diário", sq = "Shto në ditarin tim", sv = "Lägg till i min dagbok",
        tr = "Günlüğüme ekle",
    ),

    // Said once the tap is over. The four ways a tap fails each get their own sentence, because
    // the fixes are nothing alike: move the phones, hold still, update, or the other phone had
    // nothing to offer.
    "Held for the other phone to read" to NomiTranslation(
        de = "Für das andere Telefon bereitgehalten",
        es = "Listo para que lo lea el otro teléfono",
        fr = "Prêt à être lu par l'autre téléphone",
        it = "In attesa che l'altro telefono lo legga",
        nl = "Klaar om door de andere telefoon te worden gelezen",
        pt = "Pronto para o outro telefone ler",
        sq = "Gati për t'u lexuar nga telefoni tjetër",
        sv = "Redo att den andra telefonen läser",
        tr = "Diğer telefonun okuması için hazır",
    ),
    "Added {0} shared foods to your day" to NomiTranslation(
        de = "{0} geteilte Lebensmittel zu deinem Tag hinzugefügt",
        es = "Has añadido {0} alimentos compartidos a tu día",
        fr = "{0} aliments partagés ajoutés à votre journée",
        it = "{0} alimenti condivisi aggiunti alla tua giornata",
        nl = "{0} gedeelde voedingsmiddelen aan je dag toegevoegd",
        pt = "Foram adicionados {0} alimentos partilhados ao seu dia",
        sq = "U shtuan {0} ushqime të ndarë në ditën tënde",
        sv = "{0} delade livsmedel lades till din dag",
        tr = "Paylaşılan {0} yiyecek gününe eklendi",
    ),
    "This phone has no NFC" to NomiTranslation(
        de = "Dieses Telefon hat kein NFC", es = "Este teléfono no tiene NFC",
        fr = "Ce téléphone n'a pas de NFC", it = "Questo telefono non ha l'NFC",
        nl = "Dit apparaat heeft geen NFC", pt = "Este telefone não tem NFC",
        sq = "Kjo telefon nuk ka NFC", sv = "Den här telefonen har ingen NFC",
        tr = "Bu telefonda NFC yok",
    ),
    "Turn NFC on to share" to NomiTranslation(
        de = "Schalte NFC ein, um zu teilen", es = "Activa NFC para compartir",
        fr = "Activez le NFC pour partager", it = "Attiva l'NFC per condividere",
        nl = "Zet NFC aan om te delen", pt = "Ligue o NFC para partilhar",
        sq = "Ndizni NFC-në për ta ndarë", sv = "Slå på NFC för att dela",
        tr = "Paylaşmak için NFC'yi açın",
    ),
    "Tick at least one food to share" to NomiTranslation(
        de = "Wähle mindestens ein Lebensmittel zum Teilen aus",
        es = "Marca al menos un alimento para compartir",
        fr = "Cochez au moins un aliment à partager",
        it = "Seleziona almeno un alimento da condividere",
        nl = "Vink minstens één voedsel om te delen",
        pt = "Seleciona pelo menos um alimento para partilhar",
        sq = "Shëno të paktën një ushqim për ta ndarë",
        sv = "Markera minst en matvara att dela",
        tr = "Paylaşmak için en az bir yiyeceği seçin",
    ),
    "Nomi couldn't find a phone to read" to NomiTranslation(
        de = "Nomi konnte kein Telefon zum Lesen finden",
        es = "Nomi no ha encontrado ningún teléfono que leer",
        fr = "Nomi n'a trouvé aucun téléphone à lire",
        it = "Nomi non ha trovato nessun telefono da leggere",
        nl = "Nomi kon geen telefoon vinden om te lezen",
        pt = "O Nomi não encontrou nenhum telefone para ler",
        sq = "Nomi nuk gjeti asnjë telefon për t'u lexuar",
        sv = "Nomi kunde inte hitta någon telefon att läsa",
        tr = "Nomi okunacak bir telefon bulamadı",
    ),
    "The share was interrupted, try holding them closer" to NomiTranslation(
        de = "Die Freigabe wurde unterbrochen, halte sie näher zusammen",
        es = "Se interrumpió el uso compartido, acércalos más",
        fr = "Le partage a été interrompu, rapprochez les téléphones",
        it = "La condivisione è stata interrotta, avvicina i telefoni",
        nl = "Het delen werd onderbroken, houd de telefoons dichter bij elkaar",
        pt = "A partilha foi interrompida, aproxime mais os telefones",
        sq = "Ndarja u ndërpre, i afrothem më shumë telefonat",
        sv = "Delningen avbröts, håll telefonerna närmare",
        tr = "Paylaşım kesildi, telefonları daha yakın tutun",
    ),
    "That share arrived damaged, try again" to NomiTranslation(
        de = "Diese Freigabe kam beschädigt an, versuche es erneut",
        es = "Ese uso compartido llegó dañado, inténtalo de nuevo",
        fr = "Ce partage est arrivé endommagé, réessayez",
        it = "Quella condivisione è arrivata danneggiata, riprova",
        nl = "Die sharing arriveerde beschadigd, probeer het opnieuw",
        pt = "Essa partilha chegou danificada, tente novamente",
        sq = "Ai ndarje u dërdua e dëmtuar, provo përsëri",
        sv = "Den delningen kom skadad, försök igen",
        tr = "Bu paylaşım hasarlı geldi, tekrar deneyin",
    ),
    "That phone isn't a Nomi this version can read" to NomiTranslation(
        de = "Dieses Telefon ist kein Nomi, das diese Version lesen kann",
        es = "Ese teléfono no es un Nomi que esta versión pueda leer",
        fr = "Ce téléphone n'est pas un Nomi que cette version puisse lire",
        it = "Quel telefono non è un Nomi che questa versione può leggere",
        nl = "Dat is geen Nomi die deze versie kan lezen",
        pt = "Esse telefone não é um Nomi que esta versão consiga ler",
        sq = "Ai telefon nuk është një Nomi që kjo version mund ta lexojë",
        sv = "Den telefonen är inte en Nomi som den här versionen kan läsa",
        tr = "O telefon bu sürümün okuyabileceği bir Nomi değil",
    ),
    "That wasn't a shared day" to NomiTranslation(
        de = "Das war kein geteilter Tag", es = "Eso no era un día compartido",
        fr = "Ce n'était pas une journée partagée", it = "Non era una giornata condivisa",
        nl = "Dat was geen gedeelde dag", pt = "Isso não era um dia partilhado",
        sq = "Nuk ishte ditë e ndarë", sv = "Det var inte en delad dag",
        tr = "Bu paylaşılan bir gün değildi",
    ),
    "That shared day had no food in it." to NomiTranslation(
        de = "Dieser geteilte Tag enthielt kein Lebensmittel.",
        es = "Ese día compartido no tenía ningún alimento.",
        fr = "Cette journée partagée ne contenait aucun aliment.",
        it = "Quella giornata condivisa non conteneva alimenti.",
        nl = "Die gedeelde dag bevatte geen voedingsmiddelen.",
        pt = "Esse dia partilhado não tinha nenhum alimento.",
        sq = "Ai ditë e ndarë nuk kishte ushqim.",
        sv = "Den delade dagen innehöll ingen livsmedel.",
        tr = "Paylaşılan günde hiçbir yiyecek yoktu.",
    ),
    "Nomi couldn't add that shared day." to NomiTranslation(
        de = "Nomi konnte den geteilten Tag nicht hinzufügen.",
        es = "Nomi no ha podido añadir ese día compartido.",
        fr = "Nomi n'a pas pu ajouter cette journée partagée.",
        it = "Nomi non è riuscito ad aggiungere quella giornata condivisa.",
        nl = "Nomi kon die gedeelde dag niet toevoegen.",
        pt = "O Nomi não conseguiu adicionar esse dia partilhado.",
        sq = "Nomi nuk mund ta shtonte atë ditë të ndarë.",
        sv = "Nomi kunde inte lägga till den delade dagen.",
        tr = "Nomi paylaşılan günü ekleyemedi.",
    ),
    "Added {0} shared foods to {1}." to NomiTranslation(
        de = "{0} geteilte Lebensmittel zu {1} hinzugefügt.",
        es = "Has añadido {0} alimentos compartidos a {1}.",
        fr = "{0} aliments partagés ajoutés à {1}.",
        it = "{0} alimenti condivisi aggiunti a {1}.",
        nl = "{0} gedeelde voedingsmiddelen toegevoegd aan {1}.",
        pt = "Foram adicionados {0} alimentos partilhados a {1}.",
        sq = "U shtuan {0} ushqime të ndarë te {1}.",
        sv = "{0} delade livsmedel lades till {1}.",
        tr = "Paylaşılan {0} yiyecek {1} tarihine eklendi.",
    ),
)
