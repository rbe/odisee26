/* Oracle 11g
GRANT EXECUTE ON utl_http TO rbe;
EXEC dbms_network_acl_admin.create_acl(acl => 'utlpkg.xml', description => 'Normal Access', principal => 'CONNECT', is_grant => TRUE, privilege => 'connect', start_date => null, end_date => null);
EXEC dbms_network_acl_admin.add_privilege(acl => 'utlpkg.xml', principal => 'RBE', is_grant => TRUE, privilege => 'connect', start_date => null, end_date => null); 
EXEC dbms_network_acl_admin.assign_acl(acl => 'utlpkg.xml', host => 'www.bensmann.com', lower_port => 80, upper_port => 60000);
EXEC dbms_network_acl_admin.assign_acl(acl => 'utlpkg.xml', host => 'arg0.bensmann.net', lower_port => 80, upper_port => 60000);
--EXEC httputil.get('http://www.bensmann.com/images/rbe.png');
--EXEC httputil.post(url => 'http://127.0.0.1:8080/odisee/document/generate', data => '<odisee xmlns="http://xmlns.odisee.de/v2/request"><request name="IT-Vertrag" id="1231"><template name="IT-Vertrag" outputFormat="odt" revision="LATEST"/><instructions><userfield name="test">xxx</userfield></instructions></request></odisee>', result => doc_clob, bearer_token => 'YOUR_BEARER_TOKEN');
*/

/*
SET SERVEROUTPUT ON SIZE 100000
DECLARE
	document BLOB;
	request_payload CLOB;
BEGIN
	odisee.odisee_service_url := 'http://127.0.0.1:8080';
	odisee.set_bearer_token('YOUR_BEARER_TOKEN');
	odisee.header(template => 'IT-Vertrag', id => 1, output => '/tmp', output_format => 'doc');
	odisee.addfield(name => 'firma', value => 'IRB');
	odisee.addfield(name => 'einezahl', value => 1);
	odisee.addfield(name => 'datum', value => TO_DATE('01.01.2011', 'DD.MM.YYYY'));
	odisee.footer();
	odisee.generate(document, request_payload);
	dbms_output.put_line('received blob, size: ' || length(document));
END;
/
*/
