package dev.roadmap.demo.startup;

import java.util.Map;

/** Stands in for a Vault or Secrets Manager client that must exist before the Environment is complete. */
record FakeSecretStore(Map<String, String> secrets) {
}
