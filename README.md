# LanWoW

App Android per consultare i dati di un personaggio di World of Warcraft:

- **Profilo** (classe, spec, item level, gilda) e **punteggio Mythic+** da [raider.io](https://raider.io)
- **Progressione raid** e migliori run M+
- **Log e parse per boss** da [WarcraftLogs](https://www.warcraftlogs.com)

## Download

Scarica l'ultima versione dalla pagina [Releases](https://github.com/zeo93/LanWoW/releases/latest).
L'app controlla da sola gli aggiornamenti all'avvio e propone il download della nuova versione.

## Log di WarcraftLogs

Le credenziali API sono integrate nell'APK: i log funzionano senza configurare nulla.
Volendo si possono usare credenziali proprie (create su <https://www.warcraftlogs.com/api/clients>,
redirect URL `https://localhost`, senza "Public Client") incollandole nelle **Impostazioni**.

## Build

Serve un file `secrets.properties` nella cartella del progetto (non è nel repo) con:

```
wcl.clientId=IL_TUO_CLIENT_ID
wcl.clientSecret=IL_TUO_CLIENT_SECRET
```

Poi:

```
JAVA_HOME="C:\Program Files\Android\Android Studio\jbr" gradle assembleRelease
```

L'APK firmato viene generato in `app/build/outputs/apk/release/`.
