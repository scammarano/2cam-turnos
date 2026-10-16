"""Attach a trusted portal envelope to the existing tested release, preserving all original assets."""
import base64,hashlib,json,os,re,subprocess,tempfile,zipfile
from pathlib import Path

root=Path(__file__).resolve().parents[1];assets=root/'.portal-release'
raw=(assets/'manifest.json').read_bytes();sig=(assets/'manifest.sig').read_bytes();m=json.loads(raw)
repo=os.environ['GH_REPO'];assert repo=='scammarano/2cam-turnos'
assert m['source_commit']=='76176d5b40f137b35aa6388980cc7ac777d06d4b' and m['source_workflow_run']==38973490928
def api(path):return json.loads(subprocess.check_output(['gh','api',path],text=True))
run=api(f'repos/{repo}/actions/runs/{m["source_workflow_run"]}')
assert run['conclusion']=='success' and run['head_sha']==m['source_commit']
pub=assets/'update-public.txt';der=subprocess.check_output(['openssl','pkey','-pubin','-in',str(pub),'-outform','DER'])
assert hashlib.sha256(der).hexdigest()=='1c6623f7cd4ed5b1c18b7f3254e6e050a39c6bea8f2d7cd9faaecc56287a10f4'
with tempfile.TemporaryDirectory() as temp:
    out=Path(temp);(out/'manifest.json').write_bytes(raw);(out/'signature').write_bytes(base64.b64decode(sig))
    subprocess.run(['openssl','dgst','-sha256','-verify',str(pub),'-signature',str(out/'signature'),str(out/'manifest.json')],check=True)
    data=base64.b64decode((assets/'app.b64').read_text().strip(),validate=True)
    assert len(data)==m['size'] and hashlib.sha256(data).hexdigest()==m['sha256']=='6b3c90eaa0591c02d6e20fed0ba44fbd6125807ba57133e308a4fbc6ad318d49'
    apk=out/'2cam-turnos-0.2.0-local-test.apk';apk.write_bytes(data)
    tools=Path(os.environ['ANDROID_HOME'])/'build-tools/35.0.0'
    proof=subprocess.check_output([str(tools/'apksigner'),'verify','--verbose','--print-certs','--min-sdk-version','22',str(apk)],text=True)
    cert=re.findall(r'Signer #\d+ certificate SHA-256 digest: ([a-f0-9]{64})',proof)
    assert cert==[m['certificate_sha256']]==['431172fe64dbeb2536f3f08fba96650c5ca6db3fe4cd1f098b216af30638f69c']
    badging=subprocess.check_output([str(tools/'aapt'),'dump','badging',str(apk)],text=True)
    identity=re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",badging);sdk=re.search(r"sdkVersion:'(\d+)'",badging)
    assert identity and identity[1]==m['package']=='services.twocam.turnos' and identity[2]=='200' and identity[3]==m['version_name']=='0.2.0-local-test' and sdk and sdk[1]=='22'
    assert m['platform']=='turnos-control' and m['channel']=='debug'
    tag='v0.2.0-local-test';release=api(f'repos/{repo}/releases/tags/{tag}')
    assert release['published_at'] and not release['draft'] and release['prerelease']
    originals={a['name']:a for a in release['assets']}
    assert apk.name in originals
    original=api(f'repos/{repo}/releases/assets/{originals[apk.name]["id"]}')
    assert original['digest']=='sha256:'+m['sha256'],'Published APK differs; refusing to attest a replacement'
    envelope=out/'2CAM_APK_turnos-control_v0.2.0-local-test_RSA.zip'
    with zipfile.ZipFile(envelope,'w') as z:
        for n,b in [('manifest.json',raw),('manifest.sig',sig),('app.apk',data)]:z.writestr(zipfile.ZipInfo(n),b)
    digest='sha256:'+hashlib.sha256(envelope.read_bytes()).hexdigest()
    if envelope.name in originals:
        assert api(f'repos/{repo}/releases/assets/{originals[envelope.name]["id"]}')['digest']==digest,'Refusing envelope overwrite'
    else:subprocess.run(['gh','release','upload',tag,str(envelope)],check=True)
    print('TURNOS_PORTAL_ENVELOPE_PUBLISHED',tag,digest)
