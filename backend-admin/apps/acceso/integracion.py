import urllib.error
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated
from .authentication import spring_get

class PerfilCompartidoView(APIView):
    permission_classes = [IsAuthenticated]
    def get(self, request, adoptante_id):
        if request.user.rol != "ALBERGUE":
            return Response({"error": "perfil no autorizado", "campos": {}}, status=403)
        try:
            # Spring verifica propietario del albergue y consentimiento vigente.
            profile = spring_get("/api/albergue/perfiles/" + str(adoptante_id), request.auth)
            return Response(profile)
        except urllib.error.HTTPError as error:
            return Response({"error": "perfil no autorizado", "campos": {}}, status=403 if error.code in (403,404) else 503)

class PerfilesCompartidosListView(APIView):
    permission_classes = [IsAuthenticated]
    def get(self, request):
        if request.user.rol != "ALBERGUE":
            return Response({"error": "perfil no autorizado", "campos": {}}, status=403)
        try:
            return Response(spring_get("/api/albergue/perfiles", request.auth))
        except urllib.error.HTTPError as error:
            return Response({"error": "consulta no disponible", "campos": {}}, status=403 if error.code in (403,404) else 503)

class RetiredBusinessAccessView(APIView):
    authentication_classes = []
    permission_classes = []
    def get(self, request, **kwargs):
        return Response({"error": "Utiliza la API Spring Boot de Petly.", "campos": {}}, status=410)
    post = get
    put = get
    patch = get
    delete = get


def exception_handler(exc, context):
    from rest_framework.views import exception_handler as drf_handler
    response = drf_handler(exc, context)
    if response is not None:
        data = response.data
        fields = data if isinstance(data, dict) and "detail" not in data else {}
        response.data = {"error": str(data.get("detail", "datos inválidos")) if isinstance(data,dict) else "datos inválidos", "campos": fields}
    return response
