# Nexus Patcher POC

## Telemetry

Recommended params:

```dotenv
nexus.malware.risk.enabled=false
nexus.preview.ui.switch.feedback.disabled=true
nexus.skipDefaultRepositories=true
```
    
Also i recommend to block requests to this domains:

```
nxrm.telemetry.sonatype.com
nxrm-dev.telemetry.sonatype.com
license.telemetry.sonatype.com
clm.sonatype.com
rhc.sonatype.com
rhc-pro.sonatype.com
links.sonatype.com
esp.aptrinsic.com
web-sdk.aptrinsic.com
app-be.aptrinsic.com
```


Enjoy lol :)
