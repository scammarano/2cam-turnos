"""Validate proposed OpenAPI plus required documentation. Not endpoint conformance."""
from pathlib import Path
import yaml
from openapi_spec_validator import validate

root = Path(__file__).resolve().parents[1]
contract = yaml.safe_load((root / 'docs/api-v1.yaml').read_text())
validate(contract)
assert contract['openapi'] == '3.0.3'
assert len(contract['paths']) == 9
for route, definition in contract['paths'].items():
    if 'post' in definition:
        assert {'$ref': '#/components/parameters/idempotencyKey'} in definition['post']['parameters'], route
for name in ['README.md', 'docs/plan.md', 'docs/contracts.md', 'database/README.md']:
    assert (root / name).is_file(), name
print('OpenAPI syntax, references and write idempotency requirements verified')
