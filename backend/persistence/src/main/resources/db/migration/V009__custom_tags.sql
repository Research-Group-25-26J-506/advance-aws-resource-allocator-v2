-- Custom (user-supplied) tags from the wizard's TagEditor. Stored per request and applied at
-- provision time alongside the mandatory tags (mandatory tags always win). Nullable — most
-- requests carry none.
ALTER TABLE requests ADD COLUMN custom_tags_json JSON NULL AFTER form_data_json;
