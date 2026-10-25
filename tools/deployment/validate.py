"""Validate prepared deployment files locally. Never creates cloud resources."""
import argparse
import json
import pathlib
import re
import yaml
import jsonschema

ROOT = pathlib.Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--schema', required=True, help='Local copy of https://render.com/schema/render.yaml.json')
    args = parser.parse_args()
    blueprint = yaml.safe_load((ROOT / 'render.self-hosted.yaml').read_text(encoding='utf-8'))
    schema = json.loads(pathlib.Path(args.schema).read_text(encoding='utf-8-sig'))
    jsonschema.Draft202012Validator(schema).validate(blueprint)
    services = {item['name']: item for item in blueprint['services']}
    assert len(services) == 9
    assert [name for name, item in services.items() if item['type'] == 'web'] == ['shopease-gateway']
    assert {item['region'] for item in services.values()} == {'singapore'}
    assert services['shopease-gateway']['maintenanceMode']['enabled'] is True
    assert services['shopease-gateway']['healthCheckPath'] == '/actuator/health'
    assert services['shopease-mysql']['disk']['mountPath'] == '/var/lib/mysql'
    for name, item in services.items():
        assert item['autoDeployTrigger'] == 'off'
        assert item['plan'] == '1c-2g'
        assert (ROOT / item['dockerfilePath']).is_file()
        if item['type'] == 'pserv':
            assert 'healthCheckPath' not in item, 'Render private services use TCP checks only'
        for env in item.get('envVars', []):
            if re.search('PASSWORD|SECRET|API_KEY', env.get('key', '')):
                assert 'value' not in env, 'Do not hardcode deployment secrets'
            if 'fromService' in env:
                assert env['fromService']['name'] in services
    for owner in ['auth', 'product', 'order', 'payment', 'notification', 'recommendation']:
        values = {env.get('key'): env for env in services['shopease-' + owner]['envVars']}
        assert values['DB_NAME']['value'] == 'shopease_' + owner
        assert values['DB_USERNAME']['value'] == 'shopease_' + owner
        assert values['DB_PASSWORD']['fromService']['envVarKey'] == owner.upper() + '_DB_PASSWORD'
    groups = blueprint['envVarGroups'][0]['envVars']
    assert next(env for env in groups if env['key'] == 'NOTIFICATION_DELIVERY_ENABLED')['value'] == 'false'
    profile_text = (ROOT / 'deploy/config/application-render.yml').read_text(encoding='utf-8')
    assert not re.search(r'localhost|127\.0\.0\.1|8087', profile_text)
    profile = yaml.safe_load(profile_text)
    assert profile['server']['port'] == '${PORT:10000}'
    assert profile['spring']['jpa']['hibernate']['ddl-auto'] == 'validate'
    assert profile['eureka']['instance']['prefer-ip-address'] is False
    managed = yaml.safe_load((ROOT / 'render.yaml').read_text(encoding='utf-8'))
    assert managed == yaml.safe_load((ROOT / 'render.managed.yaml').read_text(encoding='utf-8'))
    jsonschema.Draft202012Validator(schema).validate(managed)
    assert len(managed['services']) == 8
    assert [s['name'] for s in managed['services'] if s['type']=='web'] == ['shopease-gateway']
    assert next(e for e in managed['envVarGroups'][0]['envVars'] if e.get('key')=='DB_SSL_MODE')['value']=='VERIFY_IDENTITY'
    for service in managed['services']:
        assert service['autoDeployTrigger']=='off'
        values={e.get('key'):e for e in service['envVars']}
        if 'DB_NAME' in values:
            for name in ['DB_HOST','DB_USERNAME','DB_PASSWORD']:
                assert values[name]['sync'] is False
        assert all(e.get('fromService',{}).get('name')!='shopease-mysql' for e in service['envVars'])
    compose = yaml.safe_load((ROOT / 'docker-compose.yml').read_text(encoding='utf-8'))
    assert compose['name'] == 'shopease-preview'
    assert len(compose['services']) == 9
    assert 'ports' not in compose['services']['mysql']
    for name, item in compose['services'].items():
        if name != 'api-gateway':
            assert 'ports' not in item
    assert not re.search(r'PASSWORD:\s*root\b|DB_PASS[=:]\s*root\b', (ROOT / 'docker-compose.yml').read_text())
    print('PASS official Render schema; nine services; private network; owned credentials; no hardcoded secrets')
    print('PASS maintenance/delivery gates; dynamic port; no production loopback; validate-only DDL')
    print('PASS isolated Compose volume/network and Gateway-only host port')
    print('PASS managed MySQL Blueprint: eight services, external secrets, identity-verified TLS')


if __name__ == '__main__':
    main()
