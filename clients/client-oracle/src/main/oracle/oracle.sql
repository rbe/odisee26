/* Oracle 11g
GRANT EXECUTE ON utl_http TO rbe;
EXEC dbms_network_acl_admin.create_acl(acl => 'utlpkg.xml', description => 'Normal Access', principal => 'CONNECT', is_grant => TRUE, privilege => 'connect', start_date => null, end_date => null);
EXEC dbms_network_acl_admin.add_privilege(acl => 'utlpkg.xml', principal => 'RBE', is_grant => TRUE, privilege => 'connect', start_date => null, end_date => null); 
EXEC dbms_network_acl_admin.assign_acl(acl => 'utlpkg.xml', host => 'www.bensmann.com', lower_port => 80, upper_port => 60000);
EXEC dbms_network_acl_admin.assign_acl(acl => 'utlpkg.xml', host => 'arg0.bensmann.net', lower_port => 80, upper_port => 60000);
--EXEC httputil.get('http://www.bensmann.com/images/rbe.png');
--EXEC httputil.post('http://arg0.bensmann.net:8080/ooo/oooDocument/stream/1', '<o3-request><request type="IT-Vertrag" id="1231"><ooo host="localhost" port="2002" template="IT-Vertrag" output="/tmp" outputFormat="odt" pre-save-macro=""/><userfields><field name="test" post-set-macro="">xxx</field></userfields></request></o3-request>');
*/

/*
SET SERVEROUTPUT ON SIZE 100000
DECLARE
	document BLOB;
BEGIN
	odisee.odisee_service_url := 'http://arg0.bensmann.net:8080/ooo';
	odisee.header(template => 'IT-Vertrag', id => 1, output => '/tmp', output_format => 'doc');
	odisee.addfield(name => 'firma', value => 'IRB');
	odisee.addfield(name => 'einezahl', value => 1);
	odisee.addfield(name => 'datum', value => TO_DATE('01.01.2011', 'DD.MM.YYYY'));
	odisee.footer();
	odisee.generate(document);
	dbms_output.put_line('received blob, size: ' || length(document));
END;
/
*/
