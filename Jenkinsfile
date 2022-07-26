@Library('shared-library@develop') _

def pipelineConfig = [
    "services": [[name: 'backend-api', path: './backend-api'], [name: 'scanner', path: './scanner']],
    "slackChannel": "flow-build"
]

serviceCI(pipelineConfig)
