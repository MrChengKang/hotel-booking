# 酒店管理與預訂 API

Base URL：`http://localhost:8080/api`。登入／註冊沿用原本 API。瀏覽器必須設定 `credentials: include`，Postman 要保留 Cookie。每個 POST／PUT／PATCH 前先 GET `/auth/csrf`，將 `token` 放到回傳的 `headerName`（`X-CSRF-TOKEN`）。登入／登出後需重新取得 token。

回傳 DTO 不含密碼、User Entity、Hibernate proxy 或任意其他帳號資料。Host／User 身分從登入 session 取得，請求內的 `hostId`、`guestId`、`userId` 不作授權依據。無權存取別人的酒店或訂單時回傳 404；角色錯誤 403；未登入 401；輸入錯誤 400；重複資料／庫存不足／狀態衝突 409。

## 公開酒店查詢（免登入）

| 方法 | 路徑 | 說明 |
| --- | --- | --- |
| GET | `/hotels?city=Kuala%20Lumpur&page=0&size=20` | 只列出已發布酒店；city 不分大小寫、完整比對，省略即全部 |
| GET | `/hotels/{hotelId}` | 已發布酒店詳情 |
| GET | `/hotels/{hotelId}/room-types` | 啟用中的房型 |
| GET | `/hotels/{hotelId}/availability?checkIn=2027-01-01&checkOut=2027-01-03&rooms=1&guests=2` | 查詢完整空房區間及逐晚報價；日期請選酒店當地今天或之後 |

分頁回傳 `{items,page,size,totalElements,totalPages}`，page 從 0 開始，size 為 1–100。空房查詢中 `guests` 是整筆預訂總人數，同房型的各間客房採相同人數上限；缺少任一晚的庫存就不可訂。報價是當下快照，不保留庫存，建立訂單時會重新查價與檢查。

## Host 管理自己的酒店

| 方法 | 路徑 | 說明 |
| --- | --- | --- |
| GET / POST | `/host/hotels` | 分頁列出自己的酒店／建立草稿 |
| GET / PUT | `/host/hotels/{id}` | 詳情／更新名稱、描述、地址、城市 |
| PATCH | `/host/hotels/{id}/status` | 只接受 DRAFT（撤回／下架）、ARCHIVED；Host 直接指定 PUBLISHED 回傳 409 |
| POST | `/host/hotels/{id}/submit` | 完成描述、房型、照片與未來庫存後送交 Admin 審核 |
| GET / POST | `/host/hotels/{id}/room-types` | 列出自己的所有房型／建立房型 |
| PUT | `/host/room-types/{id}` | 更新名稱、描述、基本價格、是否啟用 |
| POST | `/host/room-types/{id}/inventory/initialize` | 補齊指定日期區間，不覆蓋既有庫存或價格 |
| GET | `/host/room-types/{id}/inventory?from=2027-01-01&to=2027-01-03` | 按日期列出每日庫存 |
| PUT | `/host/room-types/{id}/inventory/{date}` | 調整指定日可供應房數、封鎖數、價格和停售 |
| GET | `/host/hotels/{id}/bookings?page=0&size=20` | 自己酒店的訂單及住客姓名 |

新酒店預設 DRAFT，完成描述、房型、照片與未來可售庫存後送審，Admin 核准才發布。送審期間編輯凍結，可先撤回再修改。ARCHIVED 為終止狀態，不可重新發布或編輯；既有訂單不會消失，User 仍可依規則取消。酒店 slug、國家、貨幣、時區，以及房型 code／入住上限／床數／實際總房數目前建立後不可透過 API 修改，以避免影響既有價格與預訂；沒有實體刪除 API。

建立酒店：

```json
{"name":"STAY City Hotel","slug":"stay-city-hotel","description":"City hotel","address":"1 Main Road","city":"Kuala Lumpur","countryCode":"MY","currencyCode":"MYR","timeZone":"Asia/Kuala_Lumpur"}
```

建立房型：

```json
{"code":"DELUXE_KING","name":"Deluxe King","description":"King bed","maxGuests":2,"beds":1,"totalRooms":5,"basePrice":250.00}
```

PUT 酒店：`{"name":"New name","description":"Details","address":"1 Main Road","city":"Kuala Lumpur"}`。

PUT 房型：`{"name":"Deluxe King","description":"Details","basePrice":280.00,"active":true}`；基本價格只影響將來初始化的新日期，既有每日價格不會自動改動。

初始化庫存（from 包含、to 不包含）：

```json
{"from":"2027-01-01","to":"2027-02-01"}
```

PUT 某日庫存：

```json
{"totalRooms":5,"blockedRooms":1,"nightlyPrice":300.00,"stopSell":false}
```

Host 不能直接改 bookedRooms，當日總量不能小於已訂＋封鎖，亦不能超過房型實際總房數。

## User 預訂

| 方法 | 路徑 | 說明 |
| --- | --- | --- |
| POST | `/bookings` | 建立待付款房間保留（201）；零金額可直接確認 |
| GET | `/bookings?page=0&size=20` | 只列出自己的訂單 |
| GET | `/bookings/{id}` | 自己的訂單詳情及逐晚價格快照 |
| POST | `/bookings/{id}/cancel` | 取消自己的預訂，無 body，仍需 CSRF |

```json
{"roomTypeId":1,"checkIn":"2027-01-01","checkOut":"2027-01-03","rooms":1,"guests":2,"requestKey":"6b3e9670-ae54-4d95-9428-9fe1b83a03a9"}
```

日期區間為 1–366 晚，房間為 1–20，總人數為 1–1000 且不得超出所選房型容量。房型必須啟用，酒店必須發布，入住不可早於酒店當地今天。

前端每次新預訂產生新的 UUID `requestKey`；因網路逾時或重複點擊重送時保留相同 key 和內容。同 User 同 key 只會產生一張訂單、扣一次庫存，回傳既有訂單（仍為 201）；若內容不同則 409。已取消的 key 仍回傳原已取消訂單；重新預訂請產生新 key。

回傳 `{booking:{id,reference,hotelId,roomTypeId,hotelName,roomName,checkIn,checkOut,rooms,guests,currencyCode,total,status,paymentStatus,createdAt,cancelledAt},nights:[{date,unitPrice}]}`。新訂單使用 `PENDING_PAYMENT / UNPAID`，經 Stripe sandbox 確認後為 `CONFIRMED / PAID`；零金額為 `NOT_REQUIRED`。回應另包含 `expiresAt`。舊訂單保留原狀；已付款訂單可依政策申請全額測試退款。總價＝資料庫每晚單價加總 × 房間數，不接受前端 total，不額外加入示範版清潔費、服務費或稅。需要費用政策時應再實作後端計價規則。

逐晚價格與酒店／房型名稱以訂單快照保存，Host 後續改價不影響已有訂單。新待付款訂單會保留庫存 35 分鐘，開始 Checkout 時必要時一次延長付款窗口；只有確認 Checkout 已失效才會釋放庫存，服務逾時會保留並重試核對。舊 CONFIRMED/UNPAID 訂單不自動過期。已付款訂單透過專用退款流程取消；成功前保留庫存。詳細流程參考前端 docs/PAYMENTS.md。

取消政策：新待付款訂單先關閉或核對 Stripe Checkout，確認未付款才釋放庫存。已付款訂單須透過專用退款 API，並在酒店當地入住日凌晨前提出申請；舊未付款確認訂單只能在酒店當地入住日期之前取消，入住當天或之後回傳 409。重複取消回傳同一 CANCELLED 訂單，不再次釋放庫存。取消保留歷史與價格快照，不刪除訂單。若庫存資料被外部工具破壞，取消會回傳 409 並整筆回滾，交由人工對帳。

## 交易與限制

預訂／取消在同一交易中處理庫存與訂單。建立交易先鎖定 User（同 User 冪等請求序列化），再按酒店→房型→日期庫存取得鎖；取消額外鎖定訂單。Host 修改亦遵守酒店→房型→庫存順序。目前同酒店寫入會序列化，優先保障一致性；未來有需求再細化鎖粒度。鎖逾時／版本衝突回傳 409，可保留同 key 重試。

`stay_bookings` 與 `stay_booking_nights` 的建表 SQL 位於 `db/manual/002_bookings.sql`。沒有自動新增示範帳號、酒店或真實預訂。測試只使用 H2 與本機 HTTP，包括雙人搶最後一間、同請求並發重送、價格快照、跨帳號隔離、CSRF、停售與取消。

React 已串接酒店列表、房型與空房報價、Reserve 確認建立，以及 My Bookings 查詢／取消。前端送出 expectedTotal，遇到價格變更時重新確認，網路重試保留 requestKey。另已加入酒店／房型圖片、Host 管理頁、個人資料／收藏、評價與站內通知，以及 Stripe sandbox 付款生命週期。已加入全額測試退款；Admin 總覽、帳戶查詢、酒店審核、預訂與付款／退款查詢已包含；訂房確認信、Host 分帳與退款異常處置操作留待後續。


前端確認視窗可傳入 `expectedTotal`（已確認的酒店貨幣總價）。後端仍自行計價；價格不同時回傳 409，扣庫存與訂單全部回滾，要求重新查價並確認。既有訂單的 requestKey 重送仍會回傳原結果，避免網路中斷後重複預訂。為相容既有 API 客戶端，expectedTotal 可省略；STAY 網頁會固定提供。


## 取消與退款

GET `/api/bookings/{id}/cancellation` 查詢政策、截止時間、原幣別全額報價與退款狀態。POST `/api/bookings/{id}/refund` 傳入 `{ "expectedAmount": 149.00 }`，回傳 202 與目前狀態；僅訂單擁有者 USER 可操作，需 CSRF。

只有酒店當地入住日 00:00 前可發起全額退款。退款待確認時為 `CONFIRMED / REFUND_PENDING` 並保留庫存；成功才轉為 `CANCELLED / REFUNDED` 並一次釋放庫存。失敗為 `CONFIRMED / REFUND_FAILED`，需人工處理。Webhook 另支援 `refund.created`、`refund.updated`、`refund.failed`，需加入 Stripe listener。詳細安全重試及人工核對規則見 [REFUNDS.md](REFUNDS.md)。


## Platform Admin

`/api/admin/**` requires ADMIN. Overview, user directory and hotel review endpoints, latest version confirmation and bootstrap configuration are documented in [ADMIN.md](ADMIN.md). Public registration cannot create ADMIN. Review notes are private to the owner and Admin; published hotel/favorites responses exclude them.

Admin reservation queries are read-only:

| Method | Path (prefix `/api`) | Description |
| --- | --- | --- |
| GET | `/admin/bookings` | Paged cross-host bookings with reference/email, hotel, status and inclusive check-in date filters |
| GET | `/admin/bookings/{id}` | Booking snapshot, guest/host contacts, nightly prices and nullable saved payment/refund attempts |
| GET | `/admin/refunds` | Paged refund attempts, including processing, successful and needs-review groups |

Query parameters and safe DTO fields are described in [ADMIN.md](ADMIN.md).
These endpoints do not contact Stripe, synchronize payment states, alter bookings
or inventory, or create a refund. They use the existing schema. Payment amounts
retain their original currency and provider minor-unit amount; there is no
combined revenue total across currencies.


## Live homepage destinations

GET `/api/hotels/destinations` is public and returns up to 12 cities grouped from PUBLISHED hotels, ordered by hotel count descending then city name. City matching is case-insensitive, consistent with the hotel search. Each item contains `city`, `hotelCount`, and a nullable public `coverImageUrl` from a published hotel-level photo. Draft/archived hotels and room-level photos are excluded. These are listing counts, not date-specific availability. No database migration or sample hotel seed is needed.
