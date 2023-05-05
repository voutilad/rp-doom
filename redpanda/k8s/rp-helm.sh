#!/bin/sh
set -e

# chart | url | namespace
CHARTS=$(cat <<EOF
redpanda|https://charts.redpanda.com|redpanda
jetstack|https://charts.jetstack.io|cert-manager
EOF
)

# ugly, but why not ;-)
ADDED=0
for line in $(echo "${CHARTS}"); do
    CHART=$(echo "${line}" | awk -F'|' '{ print $1 }')
    URL=$(echo "${line}" | awk -F'|' '{ print $2 }')
    NS=$(echo "${line}" | awk -F'|' '{ print $3 }')

    # Make sure the repo is installed
    if ! helm repo list | grep "${CHART}" > /dev/null; then
        echo ">> Adding ${CHART} repo '${URL}'"
        helm repo add "${CHART}" "${URL}"
        ADDED=1
    fi

    # Install namespaces if needed
    if ! kubectl get namespace | grep "${NS}" > /dev/null; then
        echo ">> Creating namespace '${NS}'"
        kubectl create namespace "${NS}"
    fi
done

if [ $ADDED -ne 0 ]; then
    echo ">> Repo added, so updating helm"
    helm repo update
fi


# Taint the control plane node...
echo ">> Tainting control plane node"
kubectl taint node \
    -l node-role.kubernetes.io/control-plane="" \
    node-role.kubernetes.io/control-plane=:NoSchedule \
    --overwrite > /dev/null

# install cert-manager
if ! helm status cert-manager -n cert-manager | grep deployed 2>&1 > /dev/null;
then
    echo ">> Installing cert-manager..."
    helm install cert-manager jetstack/cert-manager \
        --atomic \
	--set installCRDs=true \
	--namespace cert-manager
fi

# install Redpanda
if ! helm status redpanda -n redpanda | grep deployed 2>&1 > /dev/null;
then
    echo ">> Installing redpanda..."
    helm install redpanda redpanda/redpanda \
        --atomic \
	--namespace redpanda \
	--set external.domain=redpanda.local \
	--set statefulset.initContainers.setDataDirOwnership.enabled=true
fi
