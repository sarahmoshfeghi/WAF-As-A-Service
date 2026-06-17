To build a **Multi-Tenant F5 WAF-as-a-Service (WAFaaS)** portal where customers can autonomously provision their own Virtual Servers (VS), Node Pools, Certificates, Load Balancing options, and Security Profiles, you are shifting from traditional network administration to **Infrastructure-as-Code (IaC)** and **API-driven orchestration**.

This type of automated tenant onboarding requires an orchestrated workflow engine that coordinates F5 BIG-IP with security controls and logging infrastructure.

---

## 🏗️ Architectural Framework: The AS3 Declaration

The foundation of modern F5 automation is **AS3 (Application Services 3 Extension)**. Instead of using legacy imperative commands, you send a single JSON declaration to the F5 declarative API. This declaration fully defines the tenant space, virtual servers, pools, certificates, and WAF profiles all at once, ensuring clean, multi-tenant isolation.

```
[Tenant Self-Service UI] ➔ [Orchestration Engine: Ansible/Terraform] ➔ [F5 Declarative AS3 API]

```

### Multi-Tenant Isolation Strategy

1. **F5 Administrative Partition Per Tenant:** Create a distinct F5 Partition for each customer. This keeps their node pools, virtual servers, and WAF rules mathematically isolated from other customers.
2. **Dedicated Virtual Servers (VS):** Each customer is allocated a dedicated Virtual Server IP/Port combination bound to their isolated partition.
3. **Dynamic Pool Attachment:** Let clients explicitly register backend node pool destinations (e.g., Kubernetes worker nodes, static servers).

---

## 🛠️ The Blueprint Core Automated Jobs

To construct this self-service platform, your orchestration layer (like an internal API service or a pipeline built on **Terraform**, **Ansible**, or **Python FastAPIs**) must execute four core foundational automation tasks.

### 1. Load Balancing & Routing Automation (The Core Provisioner)

* **Input Parameters Taken from Client:** Frontend Virtual IP (VIP), target port (e.g., `443`), backend server IPs (Nodes), load balancing method (e.g., Round Robin, Least Connections).
* **Automated Action:** The system creates a dedicated Partition for the tenant, builds the pool with active health monitors (like HTTP / HTTPS checks), binds the nodes, and instantiates the listening Virtual Server.

### 2. Certificate Management Automation (The Cryptographic Layer)

* **Input Parameters Taken from Client:** Customer SSL Certificate (PEM format), Private Key, and requested Cipher Strength (e.g., Standard vs. High Security).
* **Automated Action:** * **Client-Side SSL:** Automatically imports the customer's certificate/key into their isolated partition and creates a custom `Client-SSL` profile to handle frontend decryption.
* **Server-Side SSL (Optional):** If the customer requires end-to-end encryption to their backend servers, the job applies a default `Server-SSL` profile to re-encrypt traffic leaving the F5 toward the node pool.
* **Automated Lifecycle Job:** Tie this pipeline to an internal renewal mechanism (like Let's Encrypt or your customer's CA API) to push updated certificates into the AS3 declaration without disrupting active connection traffic.



### 3. Security Profile Automation (The Flexible WAF Selector)

Instead of letting customers configure complex regex patterns, give them pre-packaged T-shirt sizes (Low, Medium, High Protection) built inside **F5 Advanced WAF / ASM**:

| Profile Tier | Security Focus | Included ASM Controls |
| --- | --- | --- |
| **Tier 1: Essential** | Low Overhead / High Speed | Core OWASP Top 10 protection, basic SQLi/XSS mitigation, strict RFC validation. |
| **Tier 2: Advanced** | Compliance Oriented | Layer 7 Rate Limiting, IP Reputation blocklists, Geolocation blocking, specific server technology signatures. |
| **Tier 3: Strict** | High Risk Environment | Behavioral Anomaly Detection, strict URI diversity baseline monitoring, anti-bot mitigation, mandatory Client Certificate checking. |

* **Automated Action:** Based on the tier chosen, the orchestration engine pulls the corresponding baseline WAF template and attaches the security policy to the customer’s Virtual Server declaration.

### 4. SOC Log Management Automation (The Security Data Feed)

Multi-tenancy requires ensuring that Customer A cannot see Customer B's web logs.

* **Automated Action:** The job creates a dedicated **High-Speed Logging (HSL)** destination profile inside the tenant's partition.
* **The Log Routing Pipeline:**
```
[Raw WAF Access Event] ➔ [F5 High-Speed Logging (HSL)] ➔ [Log Stash / Fluentd Parser] ➔ [SIEM Partition (Splunk Index / Elastic Workspace)]

```


The logs are appended with a unique `tenant_id` field at the F5 level. A centralized receiver parses this metadata and indexes the data directly into an isolated workspace so the security monitoring team or the client can query metrics (like request error rates or block events) securely.

---

## 🚀 Concrete Implementation Example: AS3 JSON Declaration

When a customer submits their setup request via your UI portal, your backend application should compile those inputs into an AS3 declaration. Below is a production-grade template demonstrating how a single API call configures an isolated tenant, a secure Virtual Server, a node pool, a customized Client-SSL profile, an Advanced WAF policy, and a logging profile:

```json
{
  "$schema": "https://raw.githubusercontent.com/F5Networks/f5-appsvcs-extension/master/schema/latest/as3-schema.json",
  "class": "ADC",
  "schemaVersion": "3.0.0",
  "id": "tenant-provisioning-job-101",
  "Customer_Alpha_Partition": {
    "class": "Tenant",
    "Application_Web_Service": {
      "class": "Application",
      "template": "generic",
      "frontend_virtual_server": {
        "class": "Service_HTTPS",
        "virtualAddresses": ["192.168.10.50"],
        "virtualPort": 443,
        "pool": "customer_backend_pool",
        "clientTLS": "customer_ssl_profile",
        "policyWAF": {
          "use": "custom_waf_security_policy"
        },
        "profileLogNetwork": {
          "use": "soc_remote_logging_profile"
        }
      },
      "customer_backend_pool": {
        "class": "Pool",
        "monitors": ["http"],
        "loadBalancingMode": "least-connections-member",
        "members": [
          {
            "servicePort": 8080,
            "serverAddresses": ["10.240.1.11", "10.240.1.12"]
          }
        ]
      },
      "customer_ssl_profile": {
        "class": "TLS_Server",
        "certificates": [
          {
            "certificate": "customer_imported_cert"
          }
        ],
        "ciphers": "DEFAULT"
      },
      "customer_imported_cert": {
        "class": "Certificate",
        "certificate": "-----BEGIN CERTIFICATE-----\nMIIDXTCCAkSgAwIBAgIU...\n-----END CERTIFICATE-----",
        "privateKey": "-----BEGIN PRIVATE KEY-----\nMIIEvgIBADANBgkqhkiG...\n-----END PRIVATE KEY-----"
      },
      "custom_waf_security_policy": {
        "class": "WAF_Policy",
        "url": "https://internal-repo.local/policies/waf-tier2-advanced.xml"
      },
      "soc_remote_logging_profile": {
        "class": "Log_Profile",
        "application": {
          "securityLogProfiles": [
            {
              "use": "/Common/Log_All_Requests"
            }
          ],
          "remoteHighSpeedLogging": {
            "pool": "central_soc_siem_pool",
            "protocol": "tcp"
          }
        }
      },
      "central_soc_siem_pool": {
        "class": "Pool",
        "monitors": ["tcp"],
        "members": [
          {
            "servicePort": 514,
            "serverAddresses": ["10.100.5.25"]
          }
        ]
      }
    }
  }
}

```

### Steps to Deploy and Remove This Environment Dynamically

* **To Deploy / Modify:** Send an HTTP **POST** request containing the JSON declaration to the F5 control point endpoint: `https://<BIG-IP-IP>/mgmt/shared/appsvcs/declare`. The F5 instantly handles creating the partition, binding the security policies, and loading the certificates in a single step.
* **To Fully Remove a Tenant:** Send an HTTP **DELETE** request to `https://<BIG-IP-IP>/mgmt/shared/appsvcs/declare/Customer_Alpha_Partition`. The F5 automatically tears down all virtual servers, frees the VIP, flushes the SSL profiles, and removes the entire partition cleanly, preventing infrastructure drift.
