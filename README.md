# DESCRIERE 

**JADE File Transfer** este un sistem inteligent multi-agent dezvoltat cu JADE, conceput pentru transferul sigur și controlat de fișiere între două stații de lucru. Utilizatorul de la sursă selectează ușor fișierul printr-o interfață grafică simplă, după care acesta este fragmentat în bucăți și transmis prin intermediul unui agent specializat (FileManager) către destinatar. La receptor, fișierul se salvează într-un folder ales de utilizator, după o confirmare explicită, oferind astfel un nivel suplimentar de securitate și control. Sistemul beneficiază de descoperire automată a agenților, logging vizual în timp real și  un asistent AI bazat pe Ollama care explică în limbaj ce se întâmplă în fiecare etapă a transferului, in momentul respectiv.
Protocolul de transfer implementat este unul asincron, bazat exclusiv pe mesaje ACL (Agent Communication Language) conform standardelor FIPA, și implică următorii pași principali:

Înregistrarea și descoperirea agenților
Fiecare agent se înregistrează la Directory Facilitator cu un serviciu specific:
file-sender (SenderAgent)
file-receiver (ReceiverAgent)
file-manager (FileManagerAgent)

Inițierea transferului
SenderAgent trimite un mesaj de tip REQUEST cu ontologia „file-offer” către ReceiverAgent, conținând numele fișierului și dimensiunea acestuia.
ReceiverAgent răspunde cu:
AGREE – acceptă transferul
REFUSE – refuză (lipsă director destinație sau refuz explicit al utilizatorului)

Transmiterea datelor în bucăți
După acceptare, SenderAgent fragmentează fișierul în blocuri de 4096 octeți, fiecare bloc fiind codat în Base64.
Fiecare bucată este expediată ca mesaj INFORM cu ontologia „file-chunk” către FileManagerAgent, însoțit de parametrii:
filename
chunk-index
total-chunks

Rutarea intermediară
FileManagerAgent preia fiecare bucată și o retransmite imediat către ReceiverAgent sub forma unui mesaj INFORM cu ontologia „file-chunk-to-write”, păstrând neschimbați parametrii de identificare.
Reconstituirea și salvarea fișierului
ReceiverAgent stochează temporar bucățile într-o structură concurent-sigură.
La recepționarea tuturor fragmentelor, fișierul este reconstruit și scris pe disc în directorul prealabil selectat.
La finalizare, ReceiverAgent trimite un mesaj INFORM cu ontologia „file-write-complete” către FileManagerAgent.
Semnalizarea stării inițiale
La pornire, ReceiverAgent notifică SenderAgent prin mesaj INFORM cu ontologia „receiver-ready”, permițând astfel inițierea transferului doar atunci când destinatarul este pregătit.

### Ce face sistemul

- SenderAgent → selectează și trimite fișierul
- ReceiverAgent → primește și salvează fișierul (cu confirmare manuală)
- FileManagerAgent → intermediază transferul în bucăți (chunks)
- ManagerAgent → buton de oprire a întregului sistem
- Asistent AI (Ollama + FastAPI) → explică ce se întâmplă cu transferul

### Cerințe minime

**Java / JADE**
- JDK 8+ (recomandat 11 sau 17)
- JADE 4.6.0 sau versiune recentă compatibilă

**Python / AI**
- Python 3.9+
- Ollama instalat + model `llama3` (sau `llama3.1`, `llama3.2` etc.)
- pachete: `fastapi uvicorn ollama pydantic`

### Instalare rapidă

1. JADE  
   Descarcă de pe https://jade.tilab.com/  
   Dezarhivează oriunde (ex: `C:\jade` sau `~/jade`)

2. Ollama  
   https://ollama.com/download  
   După instalare:
   ```
   ollama pull llama3
   ```

3. Dependințe Python
   ```
   cd python
   python -m venv venv
   venv\Scripts\activate          # Windows
   # sau
   source venv/bin/activate       # Linux/macOS

   pip install fastapi uvicorn ollama pydantic
   ```

### Cum pornești totul

**Terminal 1 – Asistentul AI**
```bash
cd python
uvicorn main:app --host 0.0.0.0 --port 8000
# sau cu reload în dev:
# uvicorn main:app --reload
```

Verifică: http://localhost:8000/health

**Terminal 2 – Platforma JADE**
```bash
#  în folderul cu .class-urile compilate
java -cp "jade.jar;." agents.MainBoot          # Windows
# sau
java -cp "jade.jar:." agents.MainBoot          # Linux/macOS
```

Sau rulează direct clasa `MainBoot` din IDE.

### Cum folosești

1. Se deschid ferestrele agenților + GUI-urile Sender și Receiver
2. La Receiver → alege un folder de destinație
3. La Sender → alege un fișier → apasă „Trimite”
4. Receiver întreabă dacă vrei să primești fișierul
5. Dacă accepți → transferul pornește în bucăți
6. Poți apăsa „Intreaba ai” în oricare GUI pentru explicație în limbaj natural a stadiului actual al transmiterii.

### Oprire

Apasă butonul **Shutdown System** din fereastra ManagerAgent.

