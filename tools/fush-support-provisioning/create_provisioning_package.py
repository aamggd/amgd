#!/usr/bin/env python3
import argparse, base64, getpass, hashlib, os, time, uuid
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.serialization import pkcs12

def b64u(b): return base64.urlsafe_b64encode(b).rstrip(b"=").decode()
def b64ud(s): return base64.urlsafe_b64decode(s + "="*((4-len(s)%4)%4))

p=argparse.ArgumentParser(description="Create a signed FUSH vendor-support lifecycle package (FSP2)")
p.add_argument("--request", required=True, help="FUSH-PROVISION-REQUEST|2 token shown by the client")
p.add_argument("--pkcs12", required=True, help="Offline current FUSH Vendor signing PKCS#12")
p.add_argument("--display-name", default="FUSH Support")
p.add_argument("--hours", type=int, default=24, choices=range(1,25))
a=p.parse_args()
parts=a.request.strip().split("|")
if len(parts)!=11 or parts[0]!="FUSH-PROVISION-REQUEST" or parts[1]!="2": raise SystemExit("Invalid v2 request token")
_,_,action,installation,challenge,challenge_at,previous,target_user_id,username_b64,credential_version,key_id=parts
if action not in {"PROVISION","REBIND","ROTATE","LEGACY_CLAIM"}: raise SystemExit("Request is not an identity lifecycle action")
if int(time.time()*1000)-int(challenge_at) > 24*60*60*1000: raise SystemExit("Challenge expired")
username=b64ud(username_b64).decode()
p12_password=getpass.getpass("Vendor signing PKCS#12 password: ").encode()
support_password=getpass.getpass("FUSH Support account password for this lifecycle version: ")
if len(support_password)<15: raise SystemExit("Support password must be at least 15 characters")
key, cert, chain=pkcs12.load_key_and_certificates(open(a.pkcs12,"rb").read(),p12_password)
if key is None: raise SystemExit("PKCS#12 has no private key")
salt=os.urandom(16)
pwd_hash=hashlib.pbkdf2_hmac("sha256",support_password.encode(),salt,120000,dklen=32)
now=int(time.time()*1000); expires=now+a.hours*60*60*1000
lines=[
 "version=2", f"action={action}", f"keyId={key_id}", f"installationBinding={installation}",
 f"challenge={challenge}", f"previousPackageFingerprint={previous}", f"targetSupportUserId={target_user_id}",
 f"usernameB64={b64u(username.encode())}", f"credentialVersion={credential_version}", f"nonce={uuid.uuid4()}",
 f"displayNameB64={b64u(a.display_name.encode())}", f"passwordHash={base64.b64encode(pwd_hash).decode()}",
 f"salt={base64.b64encode(salt).decode()}", f"issuedAt={now}", f"expiresAt={expires}",
]
payload=("\n".join(lines)+"\n").encode()
sig=key.sign(payload,padding.PKCS1v15(),hashes.SHA256())
print("FSP2."+b64u(payload)+"."+b64u(sig))
