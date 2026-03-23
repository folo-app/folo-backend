ALTER TABLE stock_brand_assets
    DROP CONSTRAINT chk_stock_brand_assets_source_type;

ALTER TABLE stock_brand_assets
    ADD CONSTRAINT chk_stock_brand_assets_source_type
        CHECK (source_type IN (
            'FAVICON',
            'APPLE_TOUCH_ICON',
            'OG_IMAGE',
            'LOGO_IMAGE',
            'MANUAL'
        ));
