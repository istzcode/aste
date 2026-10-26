# PlatipoAste
Auction House per Purpur/Paper 1.21.11 con valuta fisica.

Build: `mvn clean package` (Java 21) -> `target/PlatipoAste.jar` in `plugins/`.

Setup:
1. Avvia il server una volta (genera config.yml, messages.yml, data.yml).
2. Tieni in mano l'item di ciascuna valuta ed esegui `/ahsetcurrency <moneta|pila|sacco|liquida>`.
3. I giocatori usano `/ah`. `/ah reload` ricarica config e messaggi.

Permessi: platipoaste.use (default true), platipoaste.sell (true), platipoaste.admin (op).
I guadagni delle vendite vengono accumulati in data.yml e si riscuotono dal pulsante "Riscuoti Guadagni".
