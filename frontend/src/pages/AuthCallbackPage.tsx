import { useEffect, useState } from "react";
import Alert from "@cloudscape-design/components/alert";
import Box from "@cloudscape-design/components/box";
import Spinner from "@cloudscape-design/components/spinner";
import { completeLogin } from "../auth/auth";

/** Cognito redirect target: exchanges the code for tokens, then resumes where the user left off. */
export default function AuthCallbackPage() {
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    completeLogin()
      .then((target) => {
        // full reload so the app shell re-runs initAuth with the fresh session
        window.location.replace(target);
      })
      .catch((e) => setError(String(e)));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <Box textAlign="center" padding="xxl">
      {error ? (
        <Alert type="error" header="Sign-in failed" action={<a href="/">Try again</a>}>
          {error}
        </Alert>
      ) : (
        <Spinner size="large" />
      )}
    </Box>
  );
}
