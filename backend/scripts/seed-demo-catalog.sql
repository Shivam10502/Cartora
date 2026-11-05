-- Optional LOCAL DEVELOPMENT catalog. Not a Flyway migration.
-- Safe to rerun: existing names and SKUs are left unchanged.
BEGIN;

INSERT INTO category (name, description, created_at, updated_at) VALUES
  ('Lighting', 'A softer kind of glow.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('Living', 'For your favorite corners.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('Everyday', 'Small things. A little more you.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('Workspace', 'Make a little room for focus.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (name) DO NOTHING;

INSERT INTO product (name, description, price, stock_quantity, sku, category_id, image_url, created_at, updated_at)
SELECT sample.name, sample.description, sample.price, sample.stock, sample.sku, category.category_id, sample.image_url, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (VALUES
  ('Arc Table Lamp', 'A clean silhouette and a warm pool of light. Just right for slow evenings and your favorite reading corner.', 2890.00, 18, 'CARTORA-DEMO-ARC', 'Lighting', '/images/lighting.jpg'),
  ('Nord Floor Lamp', 'Sculptural lines meet a gentle glow. An easy finishing touch for a room that feels like home.', 6490.00, 4, 'CARTORA-DEMO-NORD', 'Lighting', '/images/floor-lamp.jpg'),
  ('Oak Accent Stool', 'A versatile little seat in natural wood. Pull it up, put your feet up, or make room for one more.', 2490.00, 12, 'CARTORA-DEMO-OAK', 'Living', '/images/living.jpg'),
  ('The Sunday Chair', 'A considered shape for unhurried moments. Your new favorite place to pause.', 8990.00, 6, 'CARTORA-DEMO-SUNDAY', 'Living', '/images/chair.jpg'),
  ('Linen Soft Cushion', 'A little texture goes a long way. A soft, simple addition to the corners you spend the most time in.', 1190.00, 24, 'CARTORA-DEMO-LINEN', 'Living', '/images/cushion.jpg'),
  ('Everyday Watch', 'Simple details. Quiet confidence. A clean everyday companion that goes wherever your day takes you.', 2190.00, 15, 'CARTORA-DEMO-WATCH', 'Everyday', '/images/everyday.jpg'),
  ('The Weekend Bag', 'Keep the essentials close. An easygoing companion for everyday journeys and spontaneous little escapes.', 3490.00, 9, 'CARTORA-DEMO-BAG', 'Everyday', '/images/bag.jpg'),
  ('Morning Ritual Mug', 'For the first coffee, the afternoon tea, and the little pauses in between.', 790.00, 30, 'CARTORA-DEMO-MUG', 'Everyday', '/images/mug.jpg'),
  ('The Everyday Camera', 'Make room for the moments. A little companion for keeping the good parts close.', 12990.00, 3, 'CARTORA-DEMO-CAMERA', 'Everyday', '/images/camera.jpg'),
  ('Orbit Headphones', 'A little space for your favorite sounds. Bring a thoughtful touch to your daily listening ritual.', 4990.00, 14, 'CARTORA-DEMO-ORBIT', 'Workspace', '/images/headphones.jpg'),
  ('The Focus Desk', 'A clear space for good ideas. A simple workspace with room for the things that matter.', 12990.00, 5, 'CARTORA-DEMO-DESK', 'Workspace', '/images/workspace.jpg'),
  ('The Workday Chair', 'A considered seat for the everyday. Bring a little calm and character to your workspace.', 7490.00, 0, 'CARTORA-DEMO-WORKDAY', 'Workspace', '/images/chair.jpg')
) AS sample(name, description, price, stock, sku, category_name, image_url)
JOIN category ON category.name = sample.category_name
ON CONFLICT (sku) DO NOTHING;

COMMIT;
