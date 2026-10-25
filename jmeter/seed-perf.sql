-- Datos de prueba para JMeter adaptados al esquema real de Biblioteca Virtual.
-- Usuarios: perf001..perf500
-- Libros de prueba: 2000
-- No elimina datos existentes.

-- 1. Crear 500 usuarios de prueba sin duplicar usernames existentes.
INSERT INTO usuarios (username, password, role)
SELECT 
    'perf' || lpad(g::text, 3, '0'),
    '$2a$10$quLwU4s/7SUl.KsJnjvGpOCE6sKPv3axzRZvouQKAc0hV3UJolXmS',
    'ROLE_USER'
FROM generate_series(1, 500) AS g
ON CONFLICT (username) DO NOTHING;

-- 2. Crear los 8 generos de prueba que falten.
INSERT INTO generos (nombre)
SELECT 'Genero perf ' || g 
FROM generate_series(1, 8) AS g
WHERE NOT EXISTS (
    SELECT 1 FROM generos WHERE nombre = 'Genero perf ' || g
);

-- 3. Crear los 16 autores de prueba que falten.
INSERT INTO autores (nombre)
SELECT 'Autor perf ' || g 
FROM generate_series(1, 16) AS g
WHERE NOT EXISTS (
    SELECT 1 FROM autores WHERE nombre = 'Autor perf ' || g
);

-- 4. Crear 2000 libros de prueba (el esquema real no tiene columna stock).
INSERT INTO libros (
    titulo, 
    anio_publicacion, 
    disponible, 
    portada, 
    genero_id
)
SELECT 
    'Libro de prueba ' || g, 
    1900 + (g % 120), 
    true, 
    NULL,
    (SELECT id FROM generos WHERE nombre = 'Genero perf ' || (1 + (g % 8)))
FROM generate_series(1, 2000) AS g
WHERE NOT EXISTS (
    SELECT 1 FROM libros WHERE titulo = 'Libro de prueba ' || g
);

-- 5. Asociar cada libro de prueba con un autor.
INSERT INTO libro_autor (libro_id, autor_id)
SELECT l.id, a.id
FROM libros l
JOIN autores a ON a.nombre = 'Autor perf ' || (1 + (l.id % 16))
WHERE l.titulo LIKE 'Libro de prueba %'
AND NOT EXISTS (
    SELECT 1 FROM libro_autor la WHERE la.libro_id = l.id AND la.autor_id = a.id
);

-- 6. Verificar los datos preparados.
SELECT 
    (SELECT count(*) FROM usuarios WHERE username LIKE 'perf%') AS usuarios_perf,
    (SELECT count(*) FROM libros) AS libros_total,
    (SELECT count(*) FROM libros WHERE titulo LIKE 'Libro de prueba %') AS libros_perf,
    (SELECT count(*) FROM prestamos) AS prestamos_total;