import { createContext, useContext } from "react";
import type { Me } from "../api/types";

/**
 * Identity context. In deployed environments this is fed by the Cognito OIDC flow
 * (oidc-client-ts); locally the api's dev-header bypass answers /me directly.
 */
export const MeContext = createContext<Me | null>(null);

export function useMe(): Me | null {
  return useContext(MeContext);
}

export function hasRole(me: Me | null, ...roles: string[]): boolean {
  return !!me && roles.some((role) => me.roles.includes(role));
}
