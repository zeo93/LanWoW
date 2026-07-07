# LanWoW

App Android per consultare i dati di un personaggio di World of Warcraft:

- **Profilo** (classe, spec, item level, gilda) e **punteggio Mythic+** da [raider.io](https://raider.io)
- **Progressione raid** e migliori run M+
- **Log e parse per boss** da [WarcraftLogs](https://www.warcraftlogs.com)

## Download

Scarica l'ultima versione dalla pagina [Releases](https://github.com/zeo93/LanWoW/releases/latest).
L'app controlla da sola gli aggiornamenti all'avvio e propone il download della nuova versione.

## Log di WarcraftLogs

L'API di WarcraftLogs richiede credenziali personali (gratuite):

1. Vai su <https://www.warcraftlogs.com/api/clients> e accedi con il tuo account
2. Crea un nuovo client: nome qualsiasi, come redirect URL metti `https://localhost` (è obbligatorio ma non viene usato), non spuntare "Public Client"
3. In **Impostazioni** dell'app incolla Client ID e Client Secret

Senza credenziali l'app mostra comunque tutti i dati di raider.io.

## Build

```
JAVA_HOME="C:\Program Files\Android\Android Studio\jbr" gradle assembleRelease
```

L'APK firmato viene generato in `app/build/outputs/apk/release/`.
