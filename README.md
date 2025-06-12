# 🩺 Capture Data Service — TFM 2025

> 
Plataforma para la Extracción Automática de Conocimiento desde Datos con Técnicas de IA,PLN e Indexación Semántica.
---

## 📘 Descripción General

Este proyecto forma parte del Trabajo Fin de Máster (TFM) y tiene como objetivo desarrollar una aplicación que permita:

- Cargar y procesar ficheros clínicos (CSV / JSON)
- Extraer conocimiento útil desde textos médicos
- Enriquecer la información y almacenarla en OpenSearch
- Acceder a los resultados vía endpoints REST y visualizarlos desde Kibana / OpenSearch Dashboards

El sistema ofrece servicios como resumen de texto, análisis de sentimientos, clustering, predicción de tendencias y anonimización, entre otros.

---

## ⚙️ Tecnologías utilizadas

| Tecnología         | Rol en el sistema |
|--------------------|-------------------|
| **Java 17**         | Lenguaje principal |
| **Spring Boot 3**   | Framework backend REST |
| **OpenSearch 2.x**  | Almacenamiento e indexación |
| **Stanford CoreNLP**| Procesamiento del lenguaje natural |
| **Gson**            | Lectura y escritura de JSON |
| **Apache Commons Math** | Estadísticas y clustering |
| **OpenCSV**         | Parsing de ficheros CSV |
| **Swagger / SpringDoc** | Documentación automática de APIs |

---

## 📡 Endpoints Principales (`/api/v1/opensearch`)

Los siguientes endpoints están agrupados bajo el tag `Data Processing` y forman la base de los casos de uso:

| URL Endpoint               | Descripción |
|----------------------------|-------------|
| `/process-file`            | Procesa archivos CSV/JSON y opcionalmente los indexa |
| `/extract-triples`         | Extrae triples semánticas sujeto–relación–objeto |
| `/bigdata/summary`         | Resume estadísticamente columnas numéricas |
| `/ai/summarize`            | Genera resúmenes automáticos por registro |
| `/predict-trend`           | Predice el próximo valor en series temporales |
| `/keyword-extract`         | Extrae palabras clave representativas |
| `/anonymize-text`          | Anonimiza nombres, fechas y centros médicos |
| `/clustering`              | Agrupa registros por similaridad semántica |
| `/sentiment-analysis`      | Evalúa sentimiento por frase y en global |
| `/entity-recognition`      | Reconoce entidades nombradas (personas, fechas, etc.) |
| `/text-segmentation`       | Segmenta texto clínico por secciones temáticas |

Todos los resultados se pueden **indexar automáticamente** en OpenSearch y ser consultados desde la interfaz visual.

---

## 🧪 Casos de uso del sistema

1. **Enriquecimiento semántico**: Un informe médico cargado puede ser resumido, clasificado por sentimiento, y descompuesto en segmentos temáticos.
2. **Análisis masivo de CSV clínico**: A partir de ficheros estructurados, se obtienen estadísticas descriptivas para su análisis visual.
3. **Análisis predictivo**: Series numéricas (temperaturas, glucosa) pueden predecir su próximo valor.
4. **Agrupación temática automática**: Entradas similares son agrupadas automáticamente para facilitar análisis exploratorio.
5. **Anonimización automática**: Útil para preservar privacidad de pacientes en textos libres.
6. **Visualización en dashboards**: Toda la información procesada se encuentra indexada y es navegable desde OpenSearch Dashboards.

---


# Apéndice Técnico: Instalación y Ejecución de la Plataforma

Este apartado documenta el proceso completo para la instalación, configuración y ejecución de la plataforma, considerando dos modos operativos: con y sin integración a OpenSearch. La flexibilidad de ejecución permite tanto pruebas rápidas en entornos de desarrollo como despliegues completos con capacidades de indexación y visualización.

---

## 1. Requisitos Técnicos

La plataforma se ha desarrollado y probado con los siguientes componentes:

| Componente              | Versión recomendada |
|-------------------------|---------------------|
| Java JDK                | 17                  |
| Maven                   | 3.9+                |
| Spring Boot             | 3.4.x               |
| OpenSearch              | 2.18.0              |
| OpenSearch Dashboards   | 2.18.0              |

---

## 2. Modo de Ejecución sin OpenSearch (Standalone)

### Descripción
Este modo permite utilizar la plataforma para procesar datos locales sin realizar indexación en OpenSearch. Es útil para pruebas, desarrollo o despliegues ligeros donde no se requiere almacenamiento persistente o visualización avanzada.

### Pasos

1. Compilar y ejecutar el proyecto:

   ```bash
   mvn clean spring-boot:run
   ```

2. Acceder a la interfaz Swagger para consultar y probar los endpoints expuestos:

   ```
   http://localhost:8080/swagger-ui/index.html
   ```

3. Todos los endpoints funcionarán con normalidad. La indexación se omite de manera automática si OpenSearch no está en ejecución.

---

## 3. Modo de Ejecución con OpenSearch y Dashboards

### Descripción
Este modo activa todas las funcionalidades de la plataforma, incluyendo procesamiento, almacenamiento indexado de resultados en OpenSearch y visualización de datos a través de OpenSearch Dashboards.

### 3.1 Instalación de OpenSearch

1. Descargar OpenSearch desde: [https://opensearch.org/downloads.html](https://opensearch.org/downloads.html)
2. Extraer en una ruta local, por ejemplo:

   ```
   C:\opensearch_engine2\opensearch-2.18.0\
   ```

3. Reemplazar el archivo de configuración `config/opensearch.yml` con el proporcionado en el directorio `/config/` del proyecto.

4. Ejecutar desde consola:

   ```bash
   cd C:\opensearch_engine2\opensearch-2.18.0\bin
   opensearch.bat
   ```

5. Verificar acceso en: `http://localhost:9200/`

---

### 3.2 Instalación de OpenSearch Dashboards

1. Descargar desde la misma URL.
2. Extraer en una ruta local, por ejemplo:

   ```
   C:\opensearch_dashboards2\opensearch-dashboards-2.18.0\
   ```

3. Reemplazar `config/opensearch_dashboards.yml` con el que se encuentra en el proyecto.

4. Ejecutar:

   ```bash
   cd C:\opensearch_dashboards2\opensearch-dashboards-2.18.0\bin
   opensearch-dashboards.bat
   ```

5. Acceder a la interfaz gráfica: `http://localhost:5601/`

---

### 3.3 Arranque de la Plataforma

Con los servicios de OpenSearch y Dashboards en ejecución, iniciar la plataforma como en el modo anterior:

```bash
mvn clean spring-boot:run
```

- Swagger estará disponible en:
  ```
  http://localhost:8080/swagger-ui/index.html
  ```

Los datos procesados se indexarán automáticamente, y podrán consultarse y visualizarse mediante Dashboards.

---

## 4. Organización de la Configuración

La configuración personalizada se encuentra en:

```
/config/
│   ├── opensearch.yml
│   └── opensearch_dashboards.yml
```

Estos archivos han sido ajustados específicamente para permitir una ejecución local fluida sin necesidad de modificaciones adicionales.

---

## 5. Observaciones Finales

- Los endpoints toleran errores de conexión con OpenSearch: si no está activo, la plataforma sigue operativa.
- El sistema detecta automáticamente si OpenSearch está disponible para proceder a indexar resultados.
- Esta dualidad hace la plataforma ideal tanto para entornos de desarrollo como para entornos productivos integrados con herramientas de análisis y visualización.

## 📁 Ficheros de entrada de ejemplo

En la carpeta [`/examples`](./examples/) se incluyen varios ficheros representativos que permiten:

- Probar los distintos endpoints disponibles desde Swagger.
- Validar resultados y comprender el formato esperado.
- Utilizar datos reales/simulados para demostraciones y pruebas.


Puedes cargar cualquiera de estos archivos desde Swagger mediante el parámetro `fileName`.

## 📤 Subida y uso de archivos de entrada

Antes de utilizar cualquier endpoint de procesamiento, es necesario **cargar el archivo de entrada al servidor** usando el siguiente endpoint:


Este servicio guarda el archivo en la carpeta local `C:/uploaded_files/`.

**Pasos:**

1. Accede al endpoint `/files/upload` desde Swagger UI.
2. Sube uno de los archivos de ejemplo desde tu sistema (puedes usar los de la carpeta `/examples/`).
3. Una vez subido correctamente, el nombre del archivo (ej. `patients_sample.json`) será utilizado como parámetro `fileName` en los demás endpoints.

📌 *Ejemplo de uso:*

```bash
POST /api/v1/opensearch/sentiment-analysis?fileName=patients_sample.json
