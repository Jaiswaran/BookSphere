import re
with open('app/src/main/java/com/example/ui/components/BookComponents.kt', 'r') as f:
    content = f.read()

# Replace from "Spacer(modifier = Modifier.height(16.dp))" to the end of the SignUpDialog
target_start = """                Spacer(modifier = Modifier.height(16.dp))

                // Divider"""

target_end = """                Text(
                    text = "By signing up, credentials are saved securely in your local Room Database.",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}"""

# Actually, let's just use re.sub.
pattern = re.compile(r'                Spacer\(modifier = Modifier.height\(16.dp\)\)\n\n                // Divider.*?                Text\(\n                    text = "By signing up, credentials are saved securely in your local Room Database\.",\n                    style = MaterialTheme\.typography\.labelSmall\.copy\(fontSize = 10\.sp\),\n                    color = MaterialTheme\.colorScheme\.onSurfaceVariant,\n                    textAlign = TextAlign\.Center,\n                    modifier = Modifier\.fillMaxWidth\(\)\n                \)\n            \}\n        \}\n    \}\n\}', re.DOTALL)

replacement = """                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "By continuing, you agree to our Terms of Service and Privacy Policy.",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}"""

new_content = pattern.sub(replacement, content)
with open('app/src/main/java/com/example/ui/components/BookComponents.kt', 'w') as f:
    f.write(new_content)

