import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { Auth, clearTokens } from "./api";

interface User {
  id: string;
  email: string;
  displayName: string;
  role: string;
}

interface AuthCtx {
  user: User | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, name: string) => Promise<void>;
  logout: () => void;
}

const Ctx = createContext<AuthCtx>(null as unknown as AuthCtx);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!localStorage.getItem("tf_access")) {
      setLoading(false);
      return;
    }
    Auth.me()
      .then(setUser)
      .catch(() => clearTokens())
      .finally(() => setLoading(false));
  }, []);

  const login = async (email: string, password: string) => {
    const r = await Auth.login(email, password);
    setUser(r.user);
  };
  const register = async (email: string, password: string, name: string) => {
    const r = await Auth.register(email, password, name);
    setUser(r.user);
  };
  const logout = () => {
    clearTokens();
    setUser(null);
  };
  return <Ctx.Provider value={{ user, loading, login, register, logout }}>{children}</Ctx.Provider>;
}

export function useAuth() {
  return useContext(Ctx);
}
