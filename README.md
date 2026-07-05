# RouterSync — App Android di sincronizzazione cartelle verso HDD sul router

Progetto Android completo (Kotlin + Jetpack Compose) che implementa i requisiti richiesti:

- **Autorilevamento del protocollo**: scansiona la rete locale e prova SMB (445/139), FTP (21) e WebDAV (80/5005), suggerendo il protocollo giusto per router/NAS diversi.
- **Piani di sincronizzazione**: manuale (tasto dedicato), orario, giornaliero, settimanale, mensile — gestiti con `WorkManager`.
- **Permessi cartella locale**: tramite Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE`), l'utente sceglie esplicitamente quale cartella condividere.
- **Creazione cartella su HDD**: durante la configurazione l'app può creare/verificare la cartella di destinazione sul dispositivo remoto (SMB/FTP/WebDAV).

## Come aprire il progetto

1. Apri Android Studio (Koala o successivo consigliato).
2. "Open" → seleziona la cartella `RouterSyncApp`.
3. Lascia che Gradle sincronizzi (scaricherà le dipendenze da Google, Maven Central e JitPack — serve connessione internet).
4. Esegui su un dispositivo/emulatore con **Android 8.0 (API 26)** o superiore, connesso alla stessa rete Wi-Fi del router/HDD.

## Struttura del progetto

```
app/src/main/java/com/routersync/app/
├── MainActivity.kt              # entry point, richiesta permesso notifiche, NavHost
├── RouterSyncApplication.kt
├── discovery/
│   └── NetworkDiscovery.kt      # scansione subnet e rilevamento protocollo
├── remote/
│   ├── RemoteClient.kt          # interfaccia comune SMB/FTP/WebDAV
│   ├── SmbRemoteClient.kt       # implementazione jcifs-ng
│   ├── FtpRemoteClient.kt       # implementazione Apache Commons Net
│   ├── WebDavRemoteClient.kt    # implementazione Sardine
│   └── RemoteClientFactory.kt
├── sync/
│   └── SyncEngine.kt            # confronto file locali (SAF) vs remoti, upload/download
├── work/
│   ├── SyncWorker.kt            # CoroutineWorker con notifica foreground
│   ├── SyncScheduler.kt         # traduce ScheduleType in richieste WorkManager
│   └── BootRescheduleReceiver.kt
├── data/
│   ├── SyncProfile.kt           # modello dati (entità Room) + enum
│   ├── SyncProfileDao.kt
│   ├── AppDatabase.kt
│   └── SyncProfileRepository.kt
└── ui/
    ├── SyncViewModel.kt
    ├── theme/Theme.kt
    └── screens/
        ├── DashboardScreen.kt        # elenco profili + sync manuale
        └── ProfileWizardScreen.kt    # wizard 4 step di configurazione
```

## Come ottenere l'APK online (senza installare Android Studio)

Il progetto include già `.github/workflows/build.yml`, un workflow GitHub Actions che compila l'APK sui server di GitHub ad ogni push.

1. **Crea un account GitHub** (gratuito) su [github.com](https://github.com) se non ce l'hai già.
2. **Crea un nuovo repository**: tasto "+" in alto a destra → "New repository" → dagli un nome (es. `router-sync`) → lascialo pubblico o privato, come preferisci → "Create repository". Non aggiungere README/gitignore in questa fase.
3. **Carica il contenuto dello zip** nel repository. Due modi:
   - **Da browser (più semplice, nessun comando)**: nella pagina del repository appena creato, clicca "uploading an existing file", trascina dentro tutti i file/cartelle estratti dallo zip (incluse le cartelle `.github`, `app`, e il file `README.md`), poi "Commit changes".
   - **Da terminale (se hai git installato)**:
     ```bash
     cd RouterSyncApp
     git init
     git add .
     git commit -m "Primo commit"
     git branch -M main
     git remote add origin https://github.com/TUO-USERNAME/router-sync.git
     git push -u origin main
     ```
4. **Guarda la build partire**: vai sulla scheda **"Actions"** del repository → vedrai il workflow "Build APK" in esecuzione (dura qualche minuto).
5. **Scarica l'APK**: quando la build è verde (✔), clicca sulla build completata → in fondo alla pagina, sotto "Artifacts", trovi **RouterSync-debug-apk** → scaricalo (è uno zip contenente `app-debug.apk`).
6. **Installa sul telefono**: trasferisci `app-debug.apk` sul telefono (email a te stesso, Google Drive, cavo USB) e aprilo per installarlo. Dovrai abilitare "Installa da fonti sconosciute/app esterne" nelle impostazioni Android quando richiesto.

> Nota: essendo un APK di debug non firmato per il Play Store, Android mostrerà un avviso di sicurezza standard all'installazione: è normale per le app installate manualmente (sideload), non un problema del progetto.



- **Password in chiaro nel database**: per semplicità le credenziali sono salvate come testo nel database Room. Prima di un uso reale vanno cifrate (es. con `EncryptedSharedPreferences` o Android Keystore per cifrare il campo `password`).
- **WebDAV upload**: l'implementazione con Sardine carica l'intero file in memoria (`readBytes()`); per file molto grandi conviene passare a una libreria con supporto streaming vero (es. client OkHttp custom con `RequestBody` da stream).
- **Sync mensile**: WorkManager non ha un intervallo nativo mensile (i mesi hanno lunghezza variabile), quindi viene simulato ri-pianificando un `OneTimeWorkRequest` alla fine di ogni esecuzione.
- **Conflitti in modalità bidirezionale**: la logica attuale usa il timestamp di modifica più recente ("last write wins"). Non gestisce merge di conflitti complessi (es. stesso file modificato su entrambi i lati nello stesso istante).
- **Discovery di rete**: funziona solo su reti locali IPv4 classiche (scan da .1 a .254 sulla subnet Wi-Fi corrente). Non copre subnet più grandi di una /24 o reti con isolamento client (AP isolation), che va disattivato sul router.
- **Permesso multicast**: dichiarato in Manifest ma non ancora usato per una vera discovery mDNS/UPnP; l'attuale discovery è basata su port-scan, che è più affidabile per SMB/FTP ma meno "elegante" di mDNS.

## Possibili estensioni future

- Discovery via **NSD/mDNS** (`NsdManager`) per nomi host invece di soli IP.
- Cifratura credenziali con Android Keystore.
- Sync incrementale con hash (invece del solo timestamp) per rilevare modifiche più precisamente.
- Supporto NFS (richiede libreria dedicata, meno diffusa su Android).
- Log dettagliato/esportabile delle sincronizzazioni.
