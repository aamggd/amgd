# FUSH ERP Mobile v207 — Shipment Custody, Voucher Print, Customer Edit

## Release identity
- Application ID: `com.fush.erp.recovery`
- versionCode: `207`
- versionName: `0.15.4.158-shipment-custody-voucher-print`
- Room schema: `51` (unchanged from v206)
- No destructive migration and no database recreation.

## 1. Shipment inventory custody
New shipments now move the selected physical stock out of the source warehouse immediately and into a hidden system warehouse named `عهدة الشحنات (نظام)`.

The transfer preserves item, lot/batch, expiry and unit cost. The source warehouse receives a negative `SHIPMENT_TRANSFER_OUT` movement and the system custody warehouse receives the matching positive `SHIPMENT_TRANSFER_IN` movement.

When a sales invoice is fulfilled from a v207 shipment, the stock is consumed from shipment custody using `SHIPMENT_SALE_OUT`, so it is not deducted from the source warehouse for a second time. Legacy pre-v207 shipments remain supported by the old reservation path.

Deleting an unused v207 shipment returns all untouched custody stock to its original source warehouse before the shipment row is removed. A used shipment cannot be deleted through the unused-shipment path.

## 2. Receipt/voucher printing
Customer collection receipts use the same in-app report/PDF pipeline and full-width FUSH red branding used by sales invoices. The localized Compose context now remains discoverable as the owning Activity, avoiding the prior `تعذر فتح شاشة الطباعة خارج واجهة التطبيق` failure caused by the wrapped context.

## 3. Customer master-data editing
A dedicated `CUSTOMERS_EDIT` permission was added. Authorized users can edit a customer from the customer profile while keeping the customer code/identity unchanged.

Editable fields include Arabic/English name, phone, governorate, district, area, address description, sales channel, classification, currency, credit policy/limit/days and sales representative. Updates are audited and existing invoices/receipts remain linked to the same immutable customer ID/code.

Existing roles that already had sales posting capability receive the customer-edit permission through the v207 compatibility upgrade; access is still permission checked inside `SalesService.updateCustomer`.
