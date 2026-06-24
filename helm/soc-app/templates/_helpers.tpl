{{- define "soc-app.labels" -}}
app.kubernetes.io/managed-by: Helm
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
{{- end }}
