from __future__ import annotations

import os
import logging
from typing import Optional

from fastapi import FastAPI, HTTPException, status
from fastapi.responses import PlainTextResponse
import uvicorn

# Biblioteca oficiala ollama
import ollama

# Presupunem ca ai modelul Pydantic in fisierul models.py
from models import TransferContext

# ------------------------------
# Configurare logging
# ------------------------------
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger("jade-ollama-assistant")

app = FastAPI(
    title="JADE Transfer Assistant",
    description="Explicatii simple ale transferurilor de fisiere in sistemul multi-agent JADE",
    version="0.1.0"
)

# Modelul pe care vrei sa-l folosesti
# Verifica mai intai cu: ollama list in terminal
OLLAMA_MODEL = "llama3"   # sau "llama3.1", "llama3:8b", "llama3.2" etc.


def call_ollama(prompt: str) -> str:
    """
    Foloseste biblioteca oficiala ollama (mai fiabila decat subprocess)
    """
    try:
        # Varianta simpla - generate (potrivita pentru prompt-uri independente)
        response = ollama.generate(
            model=OLLAMA_MODEL,
            prompt=prompt,
            options={
                "temperature": 0.6,   # mai putin creativ -> raspunsuri mai predictibile
                "top_p": 0.9,
            }
        )
        return response['response'].strip()

        # Alternativa: varianta chat (daca vrei sa adaugi roluri in viitor)
        # response = ollama.chat(
        #     model=OLLAMA_MODEL,
        #     messages=[{"role": "user", "content": prompt}]
        # )
        # return response['message']['content'].strip()

    except ollama.ResponseError as e:
        logger.error(f"Eroare Ollama (raspuns): {e}")
        return f"Eroare model: {str(e)}"

    except Exception as e:
        logger.exception("Eroare grava la apel Ollama")
        return f"Eroare server Ollama: {str(e)[:150]}"


@app.post("/assistant/explain", response_class=PlainTextResponse)
async def explain_transfer(context: TransferContext):
    try:
        prompt = f"""Esti un asistent pentru un sistem multi-agent JADE.
Explica foarte pe scurt situatia transferului.

Sender:   {context.sender or "necunoscut"}
Receiver: {context.receiver or "necunoscut"}
Fisier:   {context.filename or "necunoscut"}
Dimensiune: {context.size or "necunoscuta"}
Status:   {context.status or "necunoscut"}

Loguri relevante:
{chr(10).join(context.logs) if context.logs else "nu exista loguri"}

Raspunde intr-un mesaj foarte scurt, simplu, fara diacritice, fara emoticoane.
        """.strip()

        response_text = call_ollama(prompt)

        return PlainTextResponse(
            content=response_text,
            media_type="text/plain; charset=utf-8"
        )

    except Exception as e:
        logger.exception("Eroare in endpoint /assistant/explain")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Eroare interna la generarea explicatiei"
        )


@app.get("/health")
async def health_check():
    """Verificare stare server + Ollama"""
    try:
        test_result = call_ollama("Scrie doar cuvantul OK")
        status_text = "OK" if "OK" in test_result.upper() else "Probleme cu modelul"
    except Exception as e:
        status_text = f"Eroare: {str(e)[:80]}"

    return {
        "status": "healthy",
        "ollama_status": status_text,
        "model": OLLAMA_MODEL
    }


if __name__ == "__main__":
    # reload = True doar in development
    reload = os.getenv("ENV", "development") == "development"

    uvicorn.run(
        "main:app",   # schimba daca fisierul tau are alt nume (ex: app.py -> "app:app")
        host="0.0.0.0",
        port=8000,
        reload=reload,
        log_level="info"
    )
