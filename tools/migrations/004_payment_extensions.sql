CREATE TABLE IF NOT EXISTS shopease_payment.coupon_reservations (id VARCHAR(128) PRIMARY KEY,coupon_id BIGINT NOT NULL,state VARCHAR(20) NOT NULL,request_fingerprint VARCHAR(64) NOT NULL,discount DECIMAL(38,2) NOT NULL);
CREATE TABLE IF NOT EXISTS shopease_payment.service_outbox (id VARCHAR(128) PRIMARY KEY,target VARCHAR(255) NOT NULL,payload LONGTEXT NOT NULL,delivered BIT(1) NOT NULL,attempts INT NOT NULL,created_at DATETIME(6),next_attempt_at DATETIME(6),last_error VARCHAR(120));
-- Populate snapshots only where absent; never overwrite a live service record.
UPDATE shopease_payment.payments p JOIN __SOURCE__.orders o ON o.id=p.order_id JOIN __SOURCE__.customers c ON c.id=o.customer_id
SET p.customer_id=o.customer_id,p.order_snapshot=JSON_OBJECT(
 'id',o.id,'version',o.version,'orderDate',DATE_FORMAT(o.order_date,'%Y-%m-%dT%H:%i:%s.%f'),'status',CAST(o.status AS CHAR),'paymentStatus',CAST(p.payment_status AS CHAR),'paymentMethod',o.payment_method,
 'totalAmount',o.total_amount,'discountAmount',o.discount_amount,'bundleDiscountAmount',o.bundle_discount_amount,'merchandiseTotal',o.merchandise_total,'shippingAmount',o.shipping_amount,'taxAmount',o.tax_amount,'couponCode',o.coupon_code,'shippingAddress',o.shipping_address,
 'customer',JSON_OBJECT('id',c.id,'name',c.name,'email',c.email,'phone',c.phone,'shippingAddress',c.shipping_address,'loyaltyPoints',c.loyalty_points,'tier',c.tier),
 'orderItems',(SELECT JSON_ARRAYAGG(JSON_OBJECT('id',i.id,'quantity',i.quantity,'unitPrice',i.unit_price,'lineTotal',i.line_total,'product',JSON_OBJECT('id',pr.id,'name',pr.name,'description',pr.description,'price',pr.price,'category',pr.category,'imageUrl',pr.image_url))) FROM __SOURCE__.order_items i JOIN __SOURCE__.products pr ON pr.id=i.product_id WHERE i.order_id=o.id)
) WHERE p.order_snapshot IS NULL;
UPDATE shopease_payment.group_payments p JOIN __SOURCE__.group_order_members m ON m.id=p.member_id JOIN __SOURCE__.customers c ON c.id=m.customer_id
SET p.member_snapshot=JSON_OBJECT('id',m.id,'role',m.role,'shareAmount',m.share_amount,'shareStatus',CAST(m.share_status AS CHAR),'confirmedSplitVersion',m.confirmed_split_version,'customer',JSON_OBJECT('id',c.id,'name',c.name,'email',c.email)) WHERE p.member_snapshot IS NULL;
