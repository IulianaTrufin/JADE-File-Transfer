from pydantic import BaseModel
from typing import List

class TransferContext(BaseModel):
    sender: str
    receiver: str
    filename: str
    size: int
    status: str
    logs: List[str]

class AssistantResponse(BaseModel):
    summary: str
    explanation: str
    suggestion: str
