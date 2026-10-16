-- MySQL disposable CI database only. The entire service database is discarded after CI.
START TRANSACTION;
INSERT INTO companies(id,name,slug) VALUES(1,'Demo A','demo-a'),(2,'Demo B','demo-b');
INSERT INTO sites(id,company_id,code,name,timezone) VALUES(2,1,'centro','Centro','America/Caracas'),(3,2,'centro','Centro','America/Caracas');
INSERT INTO areas(id,company_id,site_id,code,name) VALUES(5,1,2,'a','Sala A'),(6,2,3,'a','Sala A');
INSERT INTO services(id,company_id,code,name,prefix) VALUES(1,1,'general','General','A'),(2,2,'general','General','A');
INSERT INTO queues(id,company_id,site_id,area_id,service_id,code) VALUES(1,1,2,5,1,'general'),(2,2,3,6,2,'general');
INSERT INTO counters(id,company_id,site_id,area_id,code,label) VALUES(1,1,2,5,'1','Ventanilla 1');
INSERT INTO users(id,company_id,login,password_hash,display_name) VALUES(1,1,'operador','not-a-login-hash','Demo');
INSERT INTO tickets(id,company_id,site_id,area_id,queue_id,business_date,number,label,issued_at,updated_at) VALUES(1,1,2,5,1,'2026-10-16',1,'A-001',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6));
INSERT INTO calls(company_id,site_id,area_id,ticket_id,counter_id,operator_id,called_at) VALUES(1,2,5,1,1,1,UTC_TIMESTAMP(6));
DELIMITER //
CREATE PROCEDURE assert_constraints()
BEGIN
 DECLARE rejected INT DEFAULT 0;
 BEGIN
  DECLARE CONTINUE HANDLER FOR 1452 SET rejected = rejected + 1;
  INSERT INTO tickets(company_id,site_id,area_id,queue_id,business_date,number,label,issued_at,updated_at) VALUES(1,2,5,2,'2026-10-16',2,'A-002',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6));
 END;
 IF rejected != 1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Cross-tenant queue was not rejected'; END IF;
 BEGIN
  DECLARE CONTINUE HANDLER FOR 1062 SET rejected = rejected + 1;
  INSERT INTO calls(company_id,site_id,area_id,ticket_id,counter_id,operator_id,called_at) VALUES(1,2,5,1,1,1,UTC_TIMESTAMP(6));
 END;
 IF rejected != 2 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Duplicate active call was not rejected'; END IF;
 UPDATE calls SET status='finished',finished_at=UTC_TIMESTAMP(6) WHERE company_id=1 AND ticket_id=1;
 INSERT INTO calls(company_id,site_id,area_id,ticket_id,counter_id,operator_id,called_at) VALUES(1,2,5,1,1,1,UTC_TIMESTAMP(6));
END//
DELIMITER ;
CALL assert_constraints();
DROP PROCEDURE assert_constraints;
-- This database is discarded by CI. DDL above can implicitly commit fixtures.
SELECT 'Schema and tenant constraints verified' AS result;
