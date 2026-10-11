-- Backfills neighbor.district_ubigeo from the free-text district of rows
-- created before v3, matching it against the 50 districts of Lima
-- Metropolitana and Callao (shared-kernel's UbigeoCatalog, INEI 2022) without
-- case, accents or extra spaces. Rows that don't match keep a null ubigeo.
-- Idempotent: only touches rows whose ubigeo is still null. A snapshot of
-- the catalog on purpose -- a migration must not change if the catalog does.
UPDATE neighbor n
SET district_ubigeo = c.ubigeo
FROM (VALUES
    ('070101', 'CALLAO'),
    ('070102', 'BELLAVISTA'),
    ('070103', 'CARMEN DE LA LEGUA REYNOSO'),
    ('070104', 'LA PERLA'),
    ('070105', 'LA PUNTA'),
    ('070106', 'VENTANILLA'),
    ('070107', 'MI PERU'),
    ('150101', 'LIMA'),
    ('150102', 'ANCON'),
    ('150103', 'ATE'),
    ('150104', 'BARRANCO'),
    ('150105', 'BRENA'),
    ('150106', 'CARABAYLLO'),
    ('150107', 'CHACLACAYO'),
    ('150108', 'CHORRILLOS'),
    ('150109', 'CIENEGUILLA'),
    ('150110', 'COMAS'),
    ('150111', 'EL AGUSTINO'),
    ('150112', 'INDEPENDENCIA'),
    ('150113', 'JESUS MARIA'),
    ('150114', 'LA MOLINA'),
    ('150115', 'LA VICTORIA'),
    ('150116', 'LINCE'),
    ('150117', 'LOS OLIVOS'),
    ('150118', 'LURIGANCHO'),
    ('150119', 'LURIN'),
    ('150120', 'MAGDALENA DEL MAR'),
    ('150121', 'PUEBLO LIBRE'),
    ('150122', 'MIRAFLORES'),
    ('150123', 'PACHACAMAC'),
    ('150124', 'PUCUSANA'),
    ('150125', 'PUENTE PIEDRA'),
    ('150126', 'PUNTA HERMOSA'),
    ('150127', 'PUNTA NEGRA'),
    ('150128', 'RIMAC'),
    ('150129', 'SAN BARTOLO'),
    ('150130', 'SAN BORJA'),
    ('150131', 'SAN ISIDRO'),
    ('150132', 'SAN JUAN DE LURIGANCHO'),
    ('150133', 'SAN JUAN DE MIRAFLORES'),
    ('150134', 'SAN LUIS'),
    ('150135', 'SAN MARTIN DE PORRES'),
    ('150136', 'SAN MIGUEL'),
    ('150137', 'SANTA ANITA'),
    ('150138', 'SANTA MARIA DEL MAR'),
    ('150139', 'SANTA ROSA'),
    ('150140', 'SANTIAGO DE SURCO'),
    ('150141', 'SURQUILLO'),
    ('150142', 'VILLA EL SALVADOR'),
    ('150143', 'VILLA MARIA DEL TRIUNFO')
) AS c(ubigeo, normalized_name)
WHERE n.district_ubigeo IS NULL
  AND n.district IS NOT NULL
  AND upper(regexp_replace(trim(translate(n.district, 'ÁÉÍÓÚÜÑáéíóúüñ', 'AEIOUUNAEIOUUN')), '[[:space:]]+', ' ', 'g'))
      = c.normalized_name;
