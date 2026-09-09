#!/usr/bin/env python3
import argparse, base64, getpass, time, uuid
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa
from cryptography.hazmat.primitives.serialization import pkcs12

def b64u(b): return base64.urlsafe_b64encode(b).rstrip(b"=").decode()

p=argparse.ArgumentParser(description="Create a signed FUSH Vendor signing-key supersession package (FSK1)")
p.add_argument("--request", required=True, help="KEY_ROTATE FUSH-PROVISION-REQUEST|2 token")
p.add_argument("--current-pkcs12", required=True, help="Current trusted Vendor signing PKCS#12")
p.add_argument("--new-public-der", required=True, help="New RSA public key DER file")
p.add_argument("--new-key-id", required=True)
p.add_argument("--hours", type=int, default=24, choices=range(1,25))
a=p.parse_args()
parts=a.request.strip().split("|")
if len(parts)!=11 or parts[0]!="FUSH-PROVISION-REQUEST" or parts[1]!="2" or parts[2]!="KEY_ROTATE": raise SystemExit("Invalid KEY_ROTATE request")
_,_,_,installation,challenge,challenge_at,previous,target_user_id,username_b64,credential_version,current_key_id=parts
if int(time.time()*1000)-int(challenge_at) > 24*60*60*1000: raise SystemExit("Challenge expired")
password=getpass.getpass("Current Vendor signing PKCS#12 password: ").encode()
key, cert, chain=pkcs12.load_key_and_certificates(open(a.current_pkcs12,"rb").read(),password)
if key is None: raise SystemExit("Current PKCS#12 has no private key")
new_der=open(a.new_public_der,"rb").read()
new_pub=serialization.load_der_public_key(new_der)
if not isinstance(new_pub,rsa.RSAPublicKey) or new_pub.key_size < 3072: raise SystemExit("New Vendor public key must be RSA >=3072")
now=int(time.time()*1000); expires=now+a.hours*60*60*1000
lines=[
 "version=1", f"keyId={current_key_id}", f"installationBinding={installation}", f"challenge={challenge}",
 f"supersedesKeyId={current_key_id}", f"newKeyId={a.new_key_id}",
 f"newPublicKeyDerBase64={base64.b64encode(new_der).decode()}", f"nonce={uuid.uuid4()}",
 f"issuedAt={now}", f"expiresAt={expires}",
]
payload=("\n".join(lines)+"\n").encode()
sig=key.sign(payload,padding.PKCS1v15(),hashes.SHA256())
print("FSK1."+b64u(payload)+"."+b64u(sig))
