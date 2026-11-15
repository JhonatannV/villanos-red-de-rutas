export interface UserProfile {
  sub: string;
  username: string;
  email?: string;
  name?: string;
  roles: string[];
}
