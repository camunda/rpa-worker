#!/usr/bin/env bash

if [[ -z $KUBECTL ]]; then
  KUBECTL=kubectl
fi

deploymentName=$1
deploymentHost=$2
zeebeClientSecret=$($KUBECTL get secret -n $deploymentName $deploymentName-zeebe-identity-secret -o jsonpath='{.data.zeebe-secret}' | base64 -d | xargs)
keycloakAdminPassword=$($KUBECTL get secret -n $deploymentName $deploymentName-keycloak -o jsonpath='{.data.admin-password}' | base64 -d | xargs)
operateClientSecret=$($KUBECTL get secret -n $deploymentName $deploymentName-operate-identity-secret -o jsonpath='{.data.operate-secret}' | base64 -d | xargs)

echo camunda.client.mode=selfmanaged
echo camunda.client.auth.client-id=zeebe
echo camunda.client.auth.client-secret=$zeebeClientSecret
echo camunda.client.zeebe.rest-address=http://zeebe.$deploymentHost
echo camunda.client.zeebe.grpc-address=http://zeebe.$deploymentHost
echo camunda.client.identity.base-url=http://$deploymentHost/auth/
echo camunda.client.auth.issuer=http://$deploymentHost/auth/realms/camunda-platform/protocol/openid-connect/token
echo camunda.rpa.zeebe.auth-endpoint=http://$deploymentHost/auth/realms/camunda-platform/protocol/openid-connect
echo camunda.client.zeebe.base-url=http://localhost:8080/zeebe
echo camunda.rpa.secrets.camunda.secrets-endpoint=http://$deploymentHost
echo camunda.rpa.e2e.camunda-host=$deploymentHost
echo camunda.rpa.e2e.client-secret=$zeebeClientSecret
echo "# Keycloak Admin password:" $keycloakAdminPassword

