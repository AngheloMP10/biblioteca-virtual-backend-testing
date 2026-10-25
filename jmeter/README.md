# Pruebas de rendimiento con JMeter

Dos planes, ejecutados en modo consola (sin GUI) para no distorsionar las mediciones:

| Archivo | Escenario | Endpoint |
|---|---|---|
| `catalogo-get.jmx` | 1. Lectura del catálogo | `GET /libros?page={0..maxPage}&size=8` |
| `solicitud-post.jmx` | 2. Solicitud de préstamo | `POST /prestamos/solicitar/{libroId}` |

## Requisitos

- Apache JMeter 5.6.3 y la variable de entorno `JMETER_HOME` apuntando a su carpeta.
- Contenedores `biblioteca-backend` y `biblioteca-postgres` levantados (Docker Compose).
- Los scripts `.ps1` se ejecutan desde PowerShell en esta carpeta.

## Orden de ejecución

1. **Datos de prueba** (una sola vez): 500 usuarios `perf001..perf500` (contraseña `Perf123@`, sin 2FA) y 2000 libros adicionales.

   ```powershell
   Get-Content seed-perf.sql | docker exec -i biblioteca-postgres psql -U postgres -d biblioteca_db
   ```

2. **Ejecución completa** (calentamiento + 5 niveles × 2 escenarios + resumen):

   ```powershell
   .\run-perf.ps1
   ```

   Variantes: `.\run-perf.ps1 -Scenarios get -Levels 50,100` o, para buscar el punto de saturación, `.\run-perf.ps1 -Scenarios get -Levels 100 -Think 0`.

3. **Resultados** en `results\<escenario>\vus-<N>\`: `resultados.jtl` y `report\index.html` (dashboard con gráficas, sirve de evidencia). `results\resumen.csv` consolida promedio, p90/p95/p99, throughput, % de errores y `L = λ × W` (Ley de Little).

## Diseño de la prueba

- **Un token por usuario.** `prepare-data.ps1` hace login de cada usuario `perf` (sin 2FA) y guarda `tokens.csv`; el `HTTP Header Manager` inyecta `Authorization: Bearer ${token}`.
- **Sin errores de negocio.** El límite es de 3 préstamos activos por usuario, y el backend responde **500** cuando se supera. Por eso `solicitudes.csv` reparte las solicitudes en 3 rondas (máx. 3 por usuario) sobre libros con stock, y se borran los préstamos de prueba entre niveles. Con este diseño, cualquier respuesta distinta de 200 es un error real.
- **Parámetros** (`-J` en JMeter): `vus`, `ramp`, `loops`, `think` (ms), `maxPage`, `host`, `port`.
- **Ramp-up por nivel:** 1 VU → 1 s, 10 → 5 s, 25 → 10 s, 50 → 20 s, 100 → 30 s. **Think time:** 1000 ms (`Constant Timer`).
- **Enfriamiento** de 30 s entre corridas y un **calentamiento** inicial que no se mide.

## Limitaciones a declarar en el informe

- JMeter, el backend y PostgreSQL comparten la misma máquina: Intel Core i5-1135G7 (4 núcleos, 8 procesadores lógicos) y unos 8 GB de RAM (7.975 MB).
- Backend y BD corren en contenedores sobre Docker Desktop con WSL2, con su propia capa de virtualización. Por defecto WSL2 limita la VM a aproximadamente la mitad de la RAM, salvo que exista un `.wslconfig`.
- El perfil `docker` ya trae `spring.jpa.show-sql=false`, así que no hay que tocar nada para medir. El perfil no define pool de conexiones, por lo que se usa el valor por defecto de Hikari (10 conexiones).
- `run-perf.ps1` guarda `docker-stats.csv` en cada corrida (CPU y memoria de `biblioteca-backend` y `biblioteca-postgres`). `resumen.csv` incluye el promedio y el pico de CPU de cada contenedor (porcentaje de un núcleo, puede superar 100 %), para decidir si el cuello de botella está en la aplicación o en la BD.
- El perfil `docker` no ejecuta `data.sql`. `seed-perf.sql` crea sus propios géneros y autores y muestra al final cuántos usuarios, libros y préstamos hay; documenta esos valores.
- Cierra el IDE, el navegador y `ng serve` durante las corridas para no competir por CPU y memoria.
