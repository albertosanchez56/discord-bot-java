# BertiniBot - Legacy (V1)

Esta carpeta contiene el codigo original de BertiniBot V1 (Java 17, JDA 5.3, LavaPlayer 1.3.77 + yt-dlp embebido).

Se mantiene aqui como referencia historica. El proyecto activo es el V2, en la carpeta hermana `bertinibot/`.

## Compilar / ejecutar el V1

Desde la raiz del repositorio:

```bash
mvn -f legacy/pom.xml clean package
java -jar legacy/target/bertinibot-1.0-SNAPSHOT-jar-with-dependencies.jar
```

Requiere el `config.properties` con el `botToken` en `legacy/src/main/resources/`.

## Aviso

El binario `yt-dlp.exe` embebido en `src/main/resources/` es Windows-only y esta desactualizado.
LavaPlayer 1.3.77 esta abandonado desde 2021 y se rompe con frecuencia por cambios de YouTube.

Para uso real, ver la documentacion del V2 en el `README.md` de la raiz.
