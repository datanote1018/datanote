-- ============================================================
-- 信贷业务源库 一键初始化（汇总 01~07）
-- 用法：mysql -uroot -p < init-credit.sql
--   或：for f in 0?_*.sql; do mysql -uroot -p < $f; done
-- 子库：base_data / customer_center / credit_center / loan_center
--       / repay_center / risk_center / fund_center
-- ============================================================
SOURCE 01_base.sql;
SOURCE 02_customer.sql;
SOURCE 03_credit.sql;
SOURCE 04_loan.sql;
SOURCE 05_repayment.sql;
SOURCE 06_risk.sql;
SOURCE 07_fund.sql;
