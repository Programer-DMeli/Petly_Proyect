"""Evaluación opt-in de respuestas reales de Gemini usando el adaptador Java de Petly.

Ejecutar después de compilar y probar el backend:
backend-admin/.venv/bin/python scripts/evaluar-chatbot.py --output /tmp/chatbot-evaluacion.json
Consume la API configurada. No modifica la base ni publica credenciales.
"""
import argparse
import json
import tempfile
import os
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET
from dotenv import dotenv_values

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--cases", default=str(root / "scripts/fixtures/chatbot-calidad.json"))
parser.add_argument("--output", required=True)
parser.add_argument("--ids", help="IDs separados por coma para repetir casos concretos.")
args = parser.parse_args()
report = root / "backend_usuario/petly_proyect/target/surefire-reports/TEST-com.petly.asistente.ChatGeminiServiceTest.xml"
if not report.exists():
    raise SystemExit("Ejecuta primero las pruebas ChatGeminiServiceTest para preparar las dependencias de Java.")
classpath = next(item.get("value") for item in ET.parse(report).findall("./properties/property")
                 if item.get("name") == "java.class.path")
config = dotenv_values(root / "backend_usuario/petly_proyect/.env")
env = dict(os.environ, **{key: value for key, value in config.items() if value is not None})
if not env.get("GEMINI_CHAT_MODEL"):
    env["GEMINI_CHAT_MODEL"] = env.get("GEMINI_MODEL", "gemini-3.1-flash-lite")
with tempfile.TemporaryDirectory(prefix="petly-chat-eval-") as temporary:
    cases_path = args.cases
    if args.ids:
        requested = set(args.ids.split(","))
        cases = json.loads(Path(args.cases).read_text())
        selected = [case for case in cases if case["id"] in requested]
        missing = requested - {case["id"] for case in selected}
        if missing:
            raise SystemExit("IDs desconocidos: " + ", ".join(sorted(missing)))
        cases_path = str(Path(temporary) / "casos.json")
        Path(cases_path).write_text(json.dumps(selected, ensure_ascii=False))
    result = subprocess.call(["java", "-cp", classpath,
        str(root / "scripts/java/PetlyChatEvaluation.java"), cases_path, args.output], cwd=root, env=env)
raise SystemExit(result)
