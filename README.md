# jenkins-scripts
misc jenkins scripts for the jenkins console.

## Audit Agent Git Configs

Loops through jobs on the agent and prints shared library git paths and any job config with a git reference. The example output is below.

```text
Jenkins url: https://<jenkins-url>/teams-<name>/
Found github branch source endpoints: 0

Shared Libraries
Library: <name> <gitpath>

Jobs
JOB: <full_uri>
	repo: <gitpath>
	repo: <gitpath>
----- Pipeline Definition -----
<script>
-------------------------------
JOB: <full_uri>
	repo: <gitpath>
	repo: <gitpath>
```

## List All Agents

Loops through connected agents and lists name and URL

`agent: <name> <URL>`
