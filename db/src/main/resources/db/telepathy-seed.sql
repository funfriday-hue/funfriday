-- Telepathy starter deck.
-- The `wavelength_*` table names are retained as internal compatibility names.
-- Run wavelength.sql first, then run this file against the GameData database.

START TRANSACTION;

INSERT INTO wavelength_cards
  (card_key, category, left_label, right_label, target, is_active)
VALUES
  ('telepathy_everyday_001', 'Everyday life', 'Barely noticeable', 'Impossible to ignore', -26, TRUE),
  ('telepathy_food_001', 'Food', 'Quick snack', 'Full celebration meal', 42, TRUE),
  ('telepathy_travel_001', 'Travel', 'Pack only a backpack', 'Bring everything just in case', 18, TRUE),
  ('telepathy_movies_001', 'Movies', 'Watch once', 'Watch every year', 63, TRUE),
  ('telepathy_skills_001', 'Skills', 'Learn in an afternoon', 'Practice for a lifetime', -38, TRUE),
  ('telepathy_social_001', 'Social', 'Small talk', 'Deep conversation', 29, TRUE),
  ('telepathy_weekend_001', 'Weekend', 'Stay in and recharge', 'Plan every hour', -11, TRUE),
  ('telepathy_technology_001', 'Technology', 'Helpful convenience', 'Cannot live without it', 54, TRUE),
  ('telepathy_hobbies_001', 'Hobbies', 'Try it once', 'Make it your whole personality', 7, TRUE),
  ('telepathy_sports_001', 'Sports', 'Casual game', 'Championship final', -47, TRUE),
  -- These two intentionally place the 4-point zone at the extreme left/right.
  ('telepathy_edge_left_001', 'Opinions', 'Absolutely no', 'Absolutely yes', -100, TRUE),
  ('telepathy_edge_right_001', 'Habits', 'Never again', 'Every single day', 100, TRUE)
AS incoming
ON DUPLICATE KEY UPDATE
  category = incoming.category,
  left_label = incoming.left_label,
  right_label = incoming.right_label,
  target = incoming.target,
  is_active = TRUE;

COMMIT;

-- Confirm the active starter cards:
SELECT id, card_key, category, left_label, right_label, target, is_active
FROM wavelength_cards
WHERE card_key LIKE 'telepathy_%'
ORDER BY card_key;
