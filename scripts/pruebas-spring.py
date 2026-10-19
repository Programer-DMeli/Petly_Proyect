"""Pruebas H2 con secreto generado para este proceso; no requiere la base del equipo."""
import os, secrets, subprocess, sys
from pathlib import Path
root=Path(__file__).resolve().parents[1]
env=dict(os.environ,PETLY_TEST_SECRET=secrets.token_urlsafe(48))
args=sys.argv[1:] or ['test']
command=['cmd.exe','/d','/c','mvnw.cmd',*args] if os.name == 'nt' else ['bash','mvnw',*args]
raise SystemExit(subprocess.call(command,cwd=root/'backend_usuario/petly_proyect',env=env))
