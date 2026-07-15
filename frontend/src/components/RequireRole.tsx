import { ReactNode } from "react";
import Alert from "@cloudscape-design/components/alert";
import { hasRole, useMe } from "../auth/useMe";

/**
 * Role gating implemented once (2.01 enhancement): nav items AND routes both use this — never
 * hide-only in nav. Roles come from the Cognito groups claim via /me.
 */
export default function RequireRole({ roles, children }: { roles: string[]; children: ReactNode }) {
  const me = useMe();
  if (!hasRole(me, ...roles)) {
    return (
      <Alert type="error" header="Access denied">
        This page requires one of the following roles: {roles.join(", ")}.
      </Alert>
    );
  }
  return <>{children}</>;
}
