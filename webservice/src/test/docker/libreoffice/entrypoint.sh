#!/bin/sh
set -eu
mkdir -p /tmp/lo-profile
exec soffice \
    --headless \
    --nologo \
    --nofirststartwizard \
    --nocrashreport \
    --norestore \
    --nolockcheck \
    -env:UserInstallation=file:///tmp/lo-profile \
    --accept="socket,host=0.0.0.0,port=2002;urp;StarOffice.ServiceManager"
